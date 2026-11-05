package com.kmgtechnologies.ecommerce.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.Arrays;
import java.util.Set;

/**
 * CSRF protection where a forgery is possible: a state-changing request carrying the session cookie, which a browser
 * sends on its own. A bearer token is never sent automatically, and without a session cookie there is nothing to
 * forge: those requests are not checked (an anonymous one keeps its 401).
 */
final class SessionCsrfMatcher implements RequestMatcher {

    static final String SESSION_COOKIE = "JSESSIONID";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public boolean matches(HttpServletRequest request) {
        return !SAFE_METHODS.contains(request.getMethod())
                && !hasBearerToken(request)
                && hasSessionCookie(request);
    }

    private static boolean hasBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return authorization != null
                && authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length());
    }

    private static boolean hasSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        return cookies != null && Arrays.stream(cookies).anyMatch(cookie -> SESSION_COOKIE.equals(cookie.getName()));
    }
}
