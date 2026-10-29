package com.example.chequebookrequestportal.auth;

import com.example.chequebookrequestportal.auth.JwtTokenService.TokenType;
import com.example.chequebookrequestportal.auth.dto.AuthResponse;
import com.example.chequebookrequestportal.auth.dto.EntrustVerificationRequest;
import com.example.chequebookrequestportal.auth.dto.LoginRequest;
import com.example.chequebookrequestportal.auth.dto.MfaChallengeResponse;
import com.example.chequebookrequestportal.common.ApiException;
import com.example.chequebookrequestportal.config.CacheConfig;
import com.example.chequebookrequestportal.domain.PortalRole;
import com.example.chequebookrequestportal.domain.StaffUser;
import com.example.chequebookrequestportal.repository.StaffUserRepository;
import com.example.chequebookrequestportal.security.PortalPrincipal;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

        private final StaffUserRepository users;
        private final AdAuthenticationClient adAuthenticationClient;
        private final JwtTokenService tokens;
        private final LoginAttemptService attempts;
        private final MfaChallengeService mfaChallenges;
        private final EntrustAuthenticationClient entrustAuthenticationClient;

        public AuthService(
                        StaffUserRepository users,
                        AdAuthenticationClient adAuthenticationClient,
                        JwtTokenService tokens,
                        LoginAttemptService attempts,
                        MfaChallengeService mfaChallenges,
                        EntrustAuthenticationClient entrustAuthenticationClient) {

                this.users = users;
                this.adAuthenticationClient = adAuthenticationClient;
                this.tokens = tokens;
                this.attempts = attempts;
                this.mfaChallenges = mfaChallenges;
                this.entrustAuthenticationClient = entrustAuthenticationClient;
        }

         public MfaChallengeResponse login(LoginRequest request) {
         StaffUser user = authenticate(request);
         return mfaChallenges.create(user, entrustUsername(user.getEmail()));
         }

        // TEST ONLY: bypass Entrust MFA
//        public AuthResponse login(LoginRequest request) {
//                StaffUser user = authenticate(request);
//                return tokenResponse(user);
//        }

        private StaffUser authenticate(LoginRequest request) {

                String email = request.username()
                                .trim()
                                .toLowerCase(Locale.ROOT);

                attempts.assertAllowed(email);

                StaffUser user = users.findByEmailIgnoreCase(email)
                                .orElse(null);

                if (user == null) {
                        attempts.failed(email);
                        throw notEnabled();
                }

                assertEnabled(user);

                boolean authenticated = adAuthenticationClient.authenticate(email, request.password());

                if (!authenticated) {
                        attempts.failed(email);
                        throw invalidCredentials();
                }

                attempts.succeeded(email);
                return user;
        }

        public AuthResponse verifyEntrust(EntrustVerificationRequest request) {
                MfaChallengeService.PendingChallenge challenge = mfaChallenges.beginVerification(request.challengeId());

                StaffUser user = users.findByEmailIgnoreCase(challenge.email()).orElse(null);
                if (user == null) {
                        mfaChallenges.invalidate(challenge.challengeId());
                        throw new ApiException(HttpStatus.UNAUTHORIZED, "MFA_CHALLENGE_INVALID",
                                        "The verification challenge is invalid or expired.");
                }

                try {
                        assertEnabled(user);
                        boolean authenticated = entrustAuthenticationClient.validate(
                                        challenge.entrustUsername(), request.token());
                        if (!authenticated) {
                                mfaChallenges.verificationFailed(challenge.challengeId());
                                throw new ApiException(HttpStatus.UNAUTHORIZED, "ENTRUST_TOKEN_INVALID",
                                                "The Entrust token is invalid or expired.");
                        }
                        mfaChallenges.complete(challenge.challengeId());
                        return tokenResponse(user);
                } catch (ApiException exception) {
                        if ("ENTRUST_UNAVAILABLE".equals(exception.getCode())) {
                                mfaChallenges.verificationUnavailable(challenge.challengeId());
                        } else if (!"ENTRUST_TOKEN_INVALID".equals(exception.getCode())
                                        && !"MFA_ATTEMPTS_EXCEEDED".equals(exception.getCode())) {
                                mfaChallenges.invalidate(challenge.challengeId());
                        }
                        throw exception;
                }
        }

        public AuthResponse refresh(String refreshToken) {

                String email = tokens.username(refreshToken);

                if (email == null) {
                        throw invalidRefreshToken();
                }

                StaffUser user = users.findByEmailIgnoreCase(email)
                                .orElseThrow(this::invalidRefreshToken);

                assertEnabled(user);

                if (!tokens.isValid(
                                refreshToken,
                                user.getTokenVersion(),
                                TokenType.REFRESH)) {
                        throw invalidRefreshToken();
                }

                return tokenResponse(user);
        }

        @Transactional
        @CacheEvict(value = CacheConfig.PROFILES, key = "#email.toLowerCase()")
        public void logout(String email) {

                StaffUser user = users.findByEmailIgnoreCase(email)
                                .orElseThrow(
                                                () -> new ApiException(
                                                                HttpStatus.NOT_FOUND,
                                                                "USER_NOT_FOUND",
                                                                "User not found."));

                user.invalidateTokens();

                users.save(user);
        }

        @Transactional
        @CacheEvict(value = CacheConfig.PROFILES, allEntries = true)
        public void logoutUsingRefreshToken(String refreshToken) {

                String email = tokens.username(refreshToken);

                if (email == null) {
                        return;
                }

                StaffUser user = users.findByEmailIgnoreCase(email)
                                .orElse(null);

                if (user == null
                                || !tokens.isValid(
                                                refreshToken,
                                                user.getTokenVersion(),
                                                TokenType.REFRESH)) {
                        return;
                }

                user.invalidateTokens();

                users.save(user);
        }

        @Cacheable(value = CacheConfig.PROFILES, key = "#email.toLowerCase()")
        public PortalPrincipal profile(String email) {

                StaffUser user = users.findByEmailIgnoreCase(email)
                                .orElseThrow(
                                                () -> new ApiException(
                                                                HttpStatus.NOT_FOUND,
                                                                "USER_NOT_FOUND",
                                                                "User not found."));

                assertEnabled(user);

                return principal(user);
        }

        public PortalPrincipal principal(StaffUser user) {

                Set<String> roles = new LinkedHashSet<>();
                Set<String> permissions = new LinkedHashSet<>();

                for (PortalRole role : user.getRoles()) {
                        roles.add(role.getCode());
                        permissions.addAll(role.getPermissions());
                }

                String primary = roles.stream()
                                .findFirst()
                                .orElse("UNASSIGNED");

                String branchCode = user.getBranch() == null
                                ? null
                                : user.getBranch().getCode();

                String branch = user.getBranch() == null
                                ? "Head Office"
                                : user.getBranch().getName()
                                                + " - "
                                                + user.getBranch().getCode();

                return new PortalPrincipal(
                                user.getId(),
                                user.getEmail(),
                                user.getEmail(),
                                user.getFullName(),
                                primary,
                                roles,
                                permissions,
                                branchCode,
                                branch);
        }

        private AuthResponse tokenResponse(StaffUser user) {

                String role = user.getRoles()
                                .stream()
                                .map(PortalRole::getCode)
                                .findFirst()
                                .orElse("UNASSIGNED");

                return new AuthResponse(
                                user.getEmail(),
                                role,
                                tokens.accessToken(user),
                                tokens.refreshToken(user));
        }

        private String entrustUsername(String email) {
                int separator = email.indexOf('@');
                return (separator > 0 ? email.substring(0, separator) : email).toLowerCase(Locale.ROOT);
        }

        private void assertEnabled(StaffUser user) {

                if (!user.isActive()) {
                        throw new ApiException(
                                        HttpStatus.FORBIDDEN,
                                        "USER_DISABLED",
                                        "This portal account is disabled.");
                }

                if (user.getBranch() != null
                                && !user.getBranch().isActive()) {

                        throw new ApiException(
                                        HttpStatus.FORBIDDEN,
                                        "BRANCH_DISABLED",
                                        "This user's branch is disabled.");
                }
        }

        private ApiException notEnabled() {

                return new ApiException(
                                HttpStatus.UNAUTHORIZED,
                                "USER_NOT_ENABLED",
                                "This user is not enabled for the chequebook portal.");
        }

        private ApiException invalidCredentials() {

                return new ApiException(
                                HttpStatus.UNAUTHORIZED,
                                "INVALID_CREDENTIALS",
                                "The username or password is incorrect.");
        }

        private ApiException invalidRefreshToken() {

                return new ApiException(
                                HttpStatus.UNAUTHORIZED,
                                "INVALID_REFRESH_TOKEN",
                                "The refresh token is invalid.");
        }
}
