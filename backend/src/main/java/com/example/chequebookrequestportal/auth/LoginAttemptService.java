package com.example.chequebookrequestportal.auth;

import com.example.chequebookrequestportal.common.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {
    private static final Logger LOG = LoggerFactory.getLogger(LoginAttemptService.class);
    private final ConcurrentHashMap<String, AttemptWindow> failures = new ConcurrentHashMap<>();
    private final int maximumAttempts;
    private final Duration window;

    public LoginAttemptService(@Value("${app.auth.login-rate-limit.max-attempts:5}") int maximumAttempts,
                               @Value("${app.auth.login-rate-limit.window:15m}") Duration window) {
        this.maximumAttempts = maximumAttempts;
        this.window = window;
    }

    public void assertAllowed(String username) {
        String key = normalize(username);
        AttemptWindow current = failures.get(key);
        if (current == null) return;
        if (current.startedAt().plus(window).isBefore(Instant.now())) {
            failures.remove(key, current);
            return;
        }
        if (current.count() >= maximumAttempts) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_RATE_LIMITED",
                "Too many failed sign-in attempts. Try again later.", true);
        }
    }

    public void failed(String username) {
        String key = normalize(username);
        Instant now = Instant.now();
        AttemptWindow updated = failures.compute(key, (ignored, current) -> current == null
            || current.startedAt().plus(window).isBefore(now) ? new AttemptWindow(1, now)
            : new AttemptWindow(current.count() + 1, current.startedAt()));
        LOG.warn("Portal login failure recorded for account identifier; attempt {} of {}.",
            updated.count(), maximumAttempts);
    }

    public void succeeded(String username) { failures.remove(normalize(username)); }
    private String normalize(String username) { return username == null ? "" : username.trim().toLowerCase(Locale.ROOT); }
    private record AttemptWindow(int count, Instant startedAt) {}
}
