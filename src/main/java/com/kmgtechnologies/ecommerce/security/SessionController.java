package com.kmgtechnologies.ecommerce.security;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Who is signed in, for the front-end: it never sees a token. Also hands out the CSRF token, written to the
 * {@code XSRF-TOKEN} cookie and sent back in the {@code X-XSRF-TOKEN} header with every write.
 */
@Tag(name = "Session", description = "Browser session of the Backend for Frontend")
@RestController
public class SessionController {

    static final String ROLE_PREFIX = "ROLE_";

    @Operation(summary = "The browser session",
            description = """
                    Public: answers `authenticated: false` without a session. Sign in by navigating to \
                    `/oauth2/authorization/keycloak`, sign out with `POST /logout` (CSRF header), then navigate to \
                    the returned `logoutUrl`.""")
    @ApiResponse(responseCode = "200", description = "Session state, and the XSRF-TOKEN cookie")
    @GetMapping("/api/me/session")
    public Session session(Authentication authentication, CsrfToken csrf) {
        csrf.getToken(); // writes the XSRF-TOKEN cookie (tokens are generated lazily)
        if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser user)) {
            return Session.ANONYMOUS;
        }
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .sorted()
                .toList();
        return new Session(true, user.getFullName(), user.getEmail(), roles);
    }

    public record Session(boolean authenticated, String name, String email, List<String> roles) {

        static final Session ANONYMOUS = new Session(false, null, null, List.of());
    }
}
