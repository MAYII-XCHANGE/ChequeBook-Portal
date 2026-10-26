package com.example.chequebookrequestportal.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.databind.ObjectMapper;

@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(LoginRateLimitFilter.class);
    private static final String LOGIN_PATH = "/api/v1/auth/login";

    private final ObjectMapper objectMapper;
    private final int maximumFailures;
    private final long windowMillis;
    private final long blockMillis;
    private final int maximumTrackedAddresses;
    private final Clock clock;
    private final Map<String, AttemptState> attempts = new HashMap<>();

    @Autowired
    public LoginRateLimitFilter(
            ObjectMapper objectMapper,
            @Value("${app.auth.login-rate-limit.ip.max-failures:3}") int maximumFailures,
            @Value("${app.auth.login-rate-limit.ip.window:1m}") Duration window,
            @Value("${app.auth.login-rate-limit.ip.block:10m}") Duration block,
            @Value("${app.auth.login-rate-limit.ip.max-tracked-addresses:10000}") int maximumTrackedAddresses) {
        this(objectMapper, maximumFailures, window, block, maximumTrackedAddresses, Clock.systemUTC());
    }

    LoginRateLimitFilter(ObjectMapper objectMapper, int maximumFailures, Duration window,
            Duration block, int maximumTrackedAddresses, Clock clock) {
        if (maximumFailures < 1 || window == null || window.isZero() || window.isNegative()
                || block == null || block.isZero() || block.isNegative() || maximumTrackedAddresses < 1) {
            throw new IllegalArgumentException("Login IP rate-limit settings must be positive");
        }
        this.objectMapper = objectMapper;
        this.maximumFailures = maximumFailures;
        this.windowMillis = window.toMillis();
        this.blockMillis = block.toMillis();
        this.maximumTrackedAddresses = maximumTrackedAddresses;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !LOGIN_PATH.equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String address = clientAddress(request);
        long retryAfter = retryAfterIfBlocked(address);
        if (retryAfter > 0) {
            log.warn("Portal login blocked by IP rate limit: address={}, retryAfterSeconds={}",
                    address, retryAfter);
            writeRateLimitResponse(request, response, retryAfter);
            return;
        }

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        filterChain.doFilter(request, wrappedResponse);

        if (isClientFailure(request, wrappedResponse.getStatus())) {
            retryAfter = recordFailure(address);
        }

        if (retryAfter > 0) {
            log.warn("Portal login IP rate limit reached: address={}, retryAfterSeconds={}",
                    address, retryAfter);
            wrappedResponse.resetBuffer();
            writeRateLimitResponse(request, wrappedResponse, retryAfter);
        }
        wrappedResponse.copyBodyToResponse();
    }

    private boolean isClientFailure(HttpServletRequest request, int status) {
        if (!"POST".equals(request.getMethod())) return true;
        return status == HttpStatus.BAD_REQUEST.value()
                || status == HttpStatus.UNAUTHORIZED.value()
                || status == HttpStatus.FORBIDDEN.value()
                || status == HttpStatus.METHOD_NOT_ALLOWED.value();
    }

    private synchronized long retryAfterIfBlocked(String address) {
        long now = clock.millis();
        removeExpired(now);
        AttemptState state = attempts.get(address);
        return state != null && state.blockedUntil > now
                ? secondsRemaining(state.blockedUntil - now)
                : 0;
    }

    private synchronized long recordFailure(String address) {
        long now = clock.millis();
        removeExpired(now);
        AttemptState state = attempts.get(address);
        if (state == null) {
            if (attempts.size() >= maximumTrackedAddresses) {
                return secondsRemaining(windowMillis);
            }
            state = new AttemptState(now);
            attempts.put(address, state);
        }
        state.failures++;
        if (state.failures >= maximumFailures) {
            state.blockedUntil = now + blockMillis;
            return secondsRemaining(blockMillis);
        }
        return 0;
    }

    private void removeExpired(long now) {
        attempts.entrySet().removeIf(entry -> {
            AttemptState state = entry.getValue();
            return state.blockedUntil > 0
                    ? now >= state.blockedUntil
                    : now - state.windowStart >= windowMillis;
        });
    }

    private void writeRateLimitResponse(HttpServletRequest request, HttpServletResponse response,
            long retryAfter) throws IOException {
        String correlationId = correlationId(request, response);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        response.setHeader("Cache-Control", "no-store");
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        objectMapper.writeValue(response.getOutputStream(), Map.of(
                "code", "LOGIN_IP_RATE_LIMITED",
                "message", "Too many failed sign-in attempts. Try again later.",
                "fieldErrors", Map.of(),
                "retryable", true,
                "correlationId", correlationId));
    }

    private String correlationId(HttpServletRequest request, HttpServletResponse response) {
        Object existing = request.getAttribute("correlationId");
        String correlationId = existing == null ? UUID.randomUUID().toString() : existing.toString();
        request.setAttribute("correlationId", correlationId);
        response.setHeader("X-Correlation-ID", correlationId);
        return correlationId;
    }

    // Use the connection address. Caller-supplied forwarding headers are not trusted here.
    private String clientAddress(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        return address == null || address.isBlank() ? "unknown" : address;
    }

    private long secondsRemaining(long millis) {
        return millis / 1000 + (millis % 1000 == 0 ? 0 : 1);
    }

    private static final class AttemptState {
        private final long windowStart;
        private int failures;
        private long blockedUntil;

        private AttemptState(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}
