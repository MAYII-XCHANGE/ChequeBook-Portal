package com.example.chequebookrequestportal.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestResponseLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestResponseLoggingFilter.class);
    private static final int MAX_LOG_LENGTH = 5000;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request, MAX_LOG_LENGTH);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        long startedAt = System.currentTimeMillis();

        log.info("Incoming request: method={}, uri={}, query={}", request.getMethod(),
                request.getRequestURI(), request.getQueryString());
        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        finally {
            long durationMs = System.currentTimeMillis() - startedAt;
            log.info("Outgoing response: method={}, uri={}, status={}, requestBody={}, responseBody={}",
                    request.getMethod(), request.getRequestURI(), wrappedResponse.getStatus(),
                    bodyForLogging(request.getRequestURI(), wrappedRequest.getContentAsByteArray(),
                            wrappedRequest.getCharacterEncoding(), wrappedRequest.getContentType()),
                    bodyForLogging(request.getRequestURI(), wrappedResponse.getContentAsByteArray(),
                            wrappedResponse.getCharacterEncoding(), wrappedResponse.getContentType()));
            wrappedResponse.copyBodyToResponse();
        }
    }

    private String bodyForLogging(String uri, byte[] content, String encoding, String contentType) {
        if (uri != null && uri.startsWith("/api/v1/auth/")) {
            return "<redacted authentication payload>";
        }
        return extractBody(content, encoding, contentType);
    }

    private Map<String, String> sanitizeHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return headers;
        }
        for (String name : Collections.list(names)) {
            if ("authorization".equalsIgnoreCase(name)
                    || "x-entrust-key".equalsIgnoreCase(name)
                    || "cookie".equalsIgnoreCase(name)
                    || "set-cookie".equalsIgnoreCase(name)) {
                headers.put(name, "***");
            } else {
                headers.put(name, truncate(request.getHeader(name)));
            }
        }
        return headers;
    }

    private String extractBody(byte[] content, String encoding, String contentType) {
        if (content == null || content.length == 0) {
            return "";
        }
        if (contentType != null && (contentType.contains("application/pdf")
                || contentType.contains("octet-stream") || contentType.contains("spreadsheetml"))) {
            return "<binary:" + content.length + " bytes>";
        }
        Charset charset = encoding == null || encoding.isBlank()
                ? StandardCharsets.UTF_8
                : Charset.forName(encoding);
        return sanitize(truncate(new String(content, charset)));
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value
                .replaceAll("(?i)\"password\"\\s*:\\s*\"[^\"]*\"", "\"password\":\"***\"")
                .replaceAll("(?i)\"currentPassword\"\\s*:\\s*\"[^\"]*\"", "\"currentPassword\":\"***\"")
                .replaceAll("(?i)\"newPassword\"\\s*:\\s*\"[^\"]*\"", "\"newPassword\":\"***\"")
                .replaceAll("(?i)\"token\"\\s*:\\s*\"[^\"]*\"", "\"token\":\"***\"")
                .replaceAll("(?i)\"challengeId\"\\s*:\\s*\"[^\"]*\"", "\"challengeId\":\"***\"")
                .replaceAll("(?i)\"accessToken\"\\s*:\\s*\"[^\"]*\"", "\"accessToken\":\"***\"")
                .replaceAll("(?i)\"refreshToken\"\\s*:\\s*\"[^\"]*\"", "\"refreshToken\":\"***\"");
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_LOG_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_LOG_LENGTH) + "...";
    }
}
