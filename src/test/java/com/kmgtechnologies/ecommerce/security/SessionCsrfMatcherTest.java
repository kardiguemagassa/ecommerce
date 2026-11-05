package com.kmgtechnologies.ecommerce.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class SessionCsrfMatcherTest {

    private final SessionCsrfMatcher matcher = new SessionCsrfMatcher();

    private static MockHttpServletRequest request(String method, boolean sessionCookie, String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/me/cart/items");
        if (sessionCookie) {
            request.setCookies(new Cookie(SessionCsrfMatcher.SESSION_COOKIE, "session-id"));
        }
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }

    @Test
    void writesWithTheSessionCookieAreChecked() {
        assertThat(matcher.matches(request("POST", true, null))).isTrue();
        assertThat(matcher.matches(request("DELETE", true, null))).isTrue();
    }

    @Test
    void readsAreNeverChecked() {
        assertThat(matcher.matches(request("GET", true, null))).isFalse();
    }

    @Test
    void bearerTokenCallsAreNotChecked_evenWithACookie() {
        assertThat(matcher.matches(request("POST", true, "Bearer eyJhbGciOiJSUzI1NiJ9"))).isFalse();
        assertThat(matcher.matches(request("POST", true, "bearer eyJhbGciOiJSUzI1NiJ9"))).isFalse();
    }

    @Test
    void writesWithoutSessionHaveNothingToForge() {
        assertThat(matcher.matches(request("POST", false, null))).isFalse();
    }
}
