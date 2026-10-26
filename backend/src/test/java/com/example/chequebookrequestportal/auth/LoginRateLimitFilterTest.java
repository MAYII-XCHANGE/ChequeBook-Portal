package com.example.chequebookrequestportal.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

class LoginRateLimitFilterTest {
    private LoginRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new LoginRateLimitFilter(new ObjectMapper(), 3, Duration.ofMinutes(1),
                Duration.ofMinutes(10), 100, Clock.systemUTC());
    }

    @Test
    void blocksTheFailureThatReachesTheConfiguredLimit() throws Exception {
        MockHttpServletResponse firstResponse = execute("10.0.0.4", "POST", 401);
        MockHttpServletResponse secondResponse = execute("10.0.0.4", "POST", 401);
        MockHttpServletResponse blockedResponse = execute("10.0.0.4", "POST", 401);

        assertEquals(401, firstResponse.getStatus());
        assertEquals(401, secondResponse.getStatus());
        assertEquals(429, blockedResponse.getStatus());
        assertEquals("600", blockedResponse.getHeader("Retry-After"));
        assertTrue(blockedResponse.getContentAsString().contains("LOGIN_IP_RATE_LIMITED"));
        assertTrue(blockedResponse.getContentAsString().contains("\"retryable\":true"));
    }

    @Test
    void rejectsARequestWithoutCallingTheChainWhileTheAddressIsBlocked() throws Exception {
        execute("10.0.0.5", "POST", 401);
        execute("10.0.0.5", "POST", 401);
        execute("10.0.0.5", "POST", 401);
        MockHttpServletRequest request = loginRequest("10.0.0.5", "POST");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] chainCalled = { false };

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> chainCalled[0] = true);

        assertFalse(chainCalled[0]);
        assertEquals(429, response.getStatus());
        assertEquals("600", response.getHeader("Retry-After"));
    }

    @Test
    void doesNotCountServerFailures() throws Exception {
        execute("10.0.0.6", "POST", 503);
        execute("10.0.0.6", "POST", 503);

        MockHttpServletResponse response = execute("10.0.0.6", "POST", 200);

        assertEquals(200, response.getStatus());
    }

    @Test
    void doesNotApplyToOtherEndpoints() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/refresh-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] chainCalled = { false };

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> chainCalled[0] = true);

        assertTrue(chainCalled[0]);
    }

    private MockHttpServletResponse execute(String address, String method, int downstreamStatus) throws Exception {
        MockHttpServletRequest request = loginRequest(address, method);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response,
                (ignoredRequest, downstreamResponse) ->
                        ((HttpServletResponse) downstreamResponse).setStatus(downstreamStatus));
        return response;
    }

    private MockHttpServletRequest loginRequest(String address, String method) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/v1/auth/login");
        request.setRemoteAddr(address);
        return request;
    }
}
