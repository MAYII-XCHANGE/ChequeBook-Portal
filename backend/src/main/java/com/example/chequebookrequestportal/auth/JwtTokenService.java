package com.example.chequebookrequestportal.auth;

import com.example.chequebookrequestportal.domain.PortalRole;
import com.example.chequebookrequestportal.domain.StaffUser;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class JwtTokenService {

    public enum TokenType {
        ACCESS,
        REFRESH
    }

    private final String secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;
    private final ObjectMapper mapper;

    public JwtTokenService(
            @Value("${security.jwt.secret-key:}") String secretKey,
            @Value("${security.jwt.access-token-expiration:}") long accessTokenExpiration,
            @Value("${security.jwt.refresh-token-expiration:}") long refreshTokenExpiration,
            ObjectMapper mapper) {

        this.secretKey = secretKey;
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.mapper = mapper;
    }

    @PostConstruct
    void validateConfiguration() {

        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "JWT secret key is required"
            );
        }

        if (secretKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT secret key must contain at least 32 characters"
            );
        }

        if (accessTokenExpiration <= 0) {
            throw new IllegalStateException(
                    "JWT access token expiration must be greater than zero"
            );
        }

        if (refreshTokenExpiration <= 0) {
            throw new IllegalStateException(
                    "JWT refresh token expiration must be greater than zero"
            );
        }

        if (refreshTokenExpiration <= accessTokenExpiration) {
            throw new IllegalStateException(
                    "JWT refresh token expiration must be greater than access token expiration"
            );
        }
    }

    public String accessToken(StaffUser user) {
        return token(
                user,
                accessTokenExpiration,
                TokenType.ACCESS
        );
    }

    public String refreshToken(StaffUser user) {
        return token(
                user,
                refreshTokenExpiration,
                TokenType.REFRESH
        );
    }

    public String username(String token) {
        JsonNode claims = verifiedClaims(token);

        return claims == null
                ? null
                : claims.path("sub").asText(null);
    }

    public boolean isValid(
            String token,
            Long currentVersion,
            TokenType expectedType) {

        JsonNode claims = verifiedClaims(token);

        if (claims == null) {
            return false;
        }

        if (!expectedType.name().equals(
                claims.path("type").asText())) {
            return false;
        }

        long persistedVersion =
                currentVersion == null ? 0L : currentVersion;

        return claims.path("ver")
                .asLong(Long.MIN_VALUE) == persistedVersion;
    }

    public long accessTokenMaxAgeSeconds() {
        return accessTokenExpiration / 1000;
    }

    public long refreshTokenMaxAgeSeconds() {
        return refreshTokenExpiration / 1000;
    }

    private String token(
            StaffUser user,
            long ttlMillis,
            TokenType type) {

        try {

            String header = encode(
                    mapper.writeValueAsBytes(
                            Map.of(
                                    "alg", "HS256",
                                    "typ", "JWT"
                            )
                    )
            );

            Map<String, Object> claims =
                    new LinkedHashMap<>();

            claims.put(
                    "sub",
                    user.getEmail()
            );

            claims.put(
                    "role",
                    user.getRoles()
                            .stream()
                            .map(PortalRole::getCode)
                            .findFirst()
                            .orElse("UNASSIGNED")
            );

            claims.put(
                    "ver",
                    user.getTokenVersion() == null
                            ? 0L
                            : user.getTokenVersion()
            );

            claims.put(
                    "type",
                    type.name()
            );

            long issuedAt =
                    Instant.now().getEpochSecond();

            claims.put(
                    "iat",
                    issuedAt
            );

            claims.put(
                    "exp",
                    Instant.now()
                            .plusMillis(ttlMillis)
                            .getEpochSecond()
            );

            String payload = encode(
                    mapper.writeValueAsBytes(claims)
            );

            String unsignedToken =
                    header + "." + payload;

            return unsignedToken
                    + "."
                    + sign(unsignedToken);

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Could not create authentication token",
                    exception
            );
        }
    }

    private JsonNode verifiedClaims(String token) {

        try {

            String[] parts =
                    token == null
                            ? new String[0]
                            : token.split("\\.");

            if (parts.length != 3) {
                return null;
            }

            byte[] actual =
                    Base64.getUrlDecoder()
                            .decode(parts[2]);

            byte[] expected =
                    signature(
                            parts[0] + "." + parts[1]
                    );

            if (!MessageDigest.isEqual(
                    expected,
                    actual)) {
                return null;
            }

            JsonNode claims =
                    mapper.readTree(
                            Base64.getUrlDecoder()
                                    .decode(parts[1])
                    );

            if (claims.path("sub")
                    .asText("")
                    .isBlank()) {
                return null;
            }

            if (Instant.now().getEpochSecond()
                    >= claims.path("exp").asLong(0)) {
                return null;
            }

            return claims;

        } catch (Exception exception) {

            return null;
        }
    }

    private String sign(String value) {

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        signature(value)
                );
    }

    private byte[] signature(String value) {

        try {

            Mac mac =
                    Mac.getInstance("HmacSHA256");

            mac.init(
                    new SecretKeySpec(
                            secretKey.getBytes(
                                    StandardCharsets.UTF_8
                            ),
                            "HmacSHA256"
                    )
            );

            return mac.doFinal(
                    value.getBytes(
                            StandardCharsets.UTF_8
                    )
            );

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Could not sign authentication token",
                    exception
            );
        }
    }

    private String encode(byte[] value) {

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value);
    }
}