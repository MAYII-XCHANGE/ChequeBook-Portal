package com.example.chequebookrequestportal.auth;

import com.example.chequebookrequestportal.auth.dto.MfaChallengeResponse;
import com.example.chequebookrequestportal.common.ApiException;
import com.example.chequebookrequestportal.domain.StaffUser;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MfaChallengeService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Map<String, Challenge> challenges = new HashMap<>();
    private final Duration lifetime;
    private final int maximumAttempts;
    private final int maximumChallenges;
    private final Clock clock;

    @Autowired
    public MfaChallengeService(
            @Value("${app.auth.mfa.challenge-lifetime:5m}") Duration lifetime,
            @Value("${app.auth.mfa.max-attempts:5}") int maximumAttempts,
            @Value("${app.auth.mfa.max-challenges:10000}") int maximumChallenges) {
        this(lifetime, maximumAttempts, maximumChallenges, Clock.systemUTC());
    }

    MfaChallengeService(Duration lifetime, int maximumAttempts, int maximumChallenges, Clock clock) {
        if (lifetime.isNegative() || lifetime.isZero() || maximumAttempts < 1 || maximumChallenges < 1) {
            throw new IllegalArgumentException("MFA challenge configuration is invalid.");
        }
        this.lifetime = lifetime;
        this.maximumAttempts = maximumAttempts;
        this.maximumChallenges = maximumChallenges;
        this.clock = clock;
    }

    public synchronized MfaChallengeResponse create(StaffUser user, String entrustUsername) {
        Instant now = clock.instant();
        removeExpired(now);
        challenges.entrySet().removeIf(entry -> entry.getValue().email().equalsIgnoreCase(user.getEmail()));
        if (challenges.size() >= maximumChallenges) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MFA_UNAVAILABLE",
                    "Multi-factor authentication is temporarily unavailable.", true);
        }

        String id;
        do {
            byte[] random = new byte[32];
            RANDOM.nextBytes(random);
            id = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        } while (challenges.containsKey(id));

        challenges.put(id, new Challenge(user.getId(), user.getEmail(), entrustUsername,
                now, now.plus(lifetime), 0, false));
        return new MfaChallengeResponse(id, lifetime.toSeconds());
    }

    public synchronized PendingChallenge beginVerification(String challengeId) {
        Challenge challenge = challenges.get(challengeId);
        if (challenge == null) {
            throw invalidChallenge();
        }
        if (!clock.instant().isBefore(challenge.expiresAt())) {
            challenges.remove(challengeId);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "MFA_CHALLENGE_EXPIRED",
                    "The verification challenge is invalid or expired.");
        }
        if (challenge.failedAttempts() >= maximumAttempts) {
            challenges.remove(challengeId);
            throw attemptsExceeded();
        }
        if (challenge.verificationInProgress()) {
            throw invalidChallenge();
        }

        challenges.put(challengeId, challenge.withVerificationInProgress(true));
        return new PendingChallenge(challengeId, challenge.userId(), challenge.email(), challenge.entrustUsername());
    }

    public synchronized void verificationUnavailable(String challengeId) {
        Challenge challenge = challenges.get(challengeId);
        if (challenge != null) {
            challenges.put(challengeId, challenge.withVerificationInProgress(false));
        }
    }

    public synchronized void verificationFailed(String challengeId) {
        Challenge challenge = challenges.get(challengeId);
        if (challenge == null) {
            throw invalidChallenge();
        }
        int attempts = challenge.failedAttempts() + 1;
        if (attempts >= maximumAttempts) {
            challenges.remove(challengeId);
            throw attemptsExceeded();
        }
        challenges.put(challengeId, challenge.withFailure(attempts));
    }

    public synchronized void complete(String challengeId) {
        Challenge challenge = challenges.remove(challengeId);
        if (challenge == null || !challenge.verificationInProgress()) {
            throw invalidChallenge();
        }
    }

    public synchronized void invalidate(String challengeId) {
        challenges.remove(challengeId);
    }

    private void removeExpired(Instant now) {
        challenges.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
    }

    private ApiException invalidChallenge() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "MFA_CHALLENGE_INVALID",
                "The verification challenge is invalid or expired.");
    }

    private ApiException attemptsExceeded() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "MFA_ATTEMPTS_EXCEEDED",
                "Too many Entrust verification attempts. Start a new sign-in.");
    }

    public record PendingChallenge(String challengeId, Long userId, String email, String entrustUsername) {
    }

    private record Challenge(Long userId, String email, String entrustUsername, Instant createdAt,
            Instant expiresAt, int failedAttempts, boolean verificationInProgress) {
        Challenge withVerificationInProgress(boolean inProgress) {
            return new Challenge(userId, email, entrustUsername, createdAt, expiresAt, failedAttempts, inProgress);
        }

        Challenge withFailure(int attempts) {
            return new Challenge(userId, email, entrustUsername, createdAt, expiresAt, attempts, false);
        }
    }
}
