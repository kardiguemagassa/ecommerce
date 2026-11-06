package com.kmgtechnologies.ecommerce.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * After the API session is closed, the Keycloak session must end too: otherwise single sign-on would log the user
 * back in at once. Answers {@code 200 {"logoutUrl": ...}} rather than a redirect, so that a single-page application
 * (which calls {@code POST /logout} with its CSRF header) navigates the browser to Keycloak itself: an XHR cannot follow
 * a cross-origin redirect.
 */
final class KeycloakLogoutSuccessHandler implements LogoutSuccessHandler {

    private final BffProperties bff;

    KeycloakLogoutSuccessHandler(BffProperties bff) {
        this.bff = bff;
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"logoutUrl\":\"" + logoutUrl(request, authentication) + "\"}");
    }

    /** Keycloak end-session URL with the ID token as hint; the front-end URL when no OIDC session was open. */
    String logoutUrl(HttpServletRequest request, Authentication authentication) {
        String frontUrl = frontUrl(request);
        if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser user)) {
            return frontUrl;
        }
        return UriComponentsBuilder.fromUri(bff.endSessionUri())
                .queryParam("id_token_hint", user.getIdToken().getTokenValue())
                .queryParam("post_logout_redirect_uri", frontUrl)
                .encode()
                .build()
                .toUriString();
    }

    /**
     * Keycloak only accepts an absolute, registered post-logout URI: a relative front-end URL is resolved against the
     * address the browser used (behind nginx: its forwarded headers, see server.forward-headers-strategy).
     */
    private String frontUrl(HttpServletRequest request) {
        if (bff.frontUrl().isAbsolute()) {
            return bff.frontUrl().toString();
        }
        return ServletUriComponentsBuilder.fromContextPath(request).path(bff.frontUrl().getPath()).build().toUriString();
    }
}
