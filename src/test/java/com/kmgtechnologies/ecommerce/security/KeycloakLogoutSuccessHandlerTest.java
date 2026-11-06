package com.kmgtechnologies.ecommerce.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakLogoutSuccessHandlerTest {

    private static final BffProperties BFF = new BffProperties(
            URI.create("http://localhost:8080/api/me/session"),
            URI.create("http://localhost:8180/realms/ecommerce/protocol/openid-connect/logout"));

    private final KeycloakLogoutSuccessHandler handler = new KeycloakLogoutSuccessHandler(BFF);

    @Test
    void browserSessionEndsAtKeycloak_withTheIdTokenAsHint() throws Exception {
        OidcIdToken idToken = new OidcIdToken("id-token-value", Instant.now(), Instant.now().plusSeconds(300),
                Map.of("sub", "alice"));
        DefaultOidcUser user = new DefaultOidcUser(List.of(new OidcUserAuthority(idToken)), idToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onLogoutSuccess(new MockHttpServletRequest(), response,
                new OAuth2AuthenticationToken(user, user.getAuthorities(), "keycloak"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString())
                .startsWith("{\"logoutUrl\":\"http://localhost:8180/realms/ecommerce/protocol/openid-connect/logout?")
                .contains("id_token_hint=id-token-value")
                .contains("post_logout_redirect_uri=http://localhost:8080/api/me/session");
    }

    @Test
    void withoutBrowserSessionTheFrontEndIsTheOnlyPlaceToGo() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(handler.logoutUrl(request, null)).isEqualTo("http://localhost:8080/api/me/session");
        assertThat(handler.logoutUrl(request, new TestingAuthenticationToken("api-client", null))).isEqualTo(
                "http://localhost:8080/api/me/session");
    }

    @Test
    void relativeFrontEnd_isResolvedAgainstTheAddressTheBrowserUsed() {
        KeycloakLogoutSuccessHandler relative = new KeycloakLogoutSuccessHandler(new BffProperties(URI.create("/"),
                URI.create("http://localhost:8180/realms/ecommerce/protocol/openid-connect/logout")));
        MockHttpServletRequest throughNginx = new MockHttpServletRequest("POST", "/logout");
        throughNginx.setServerName("localhost");
        throughNginx.setServerPort(8080);
        MockHttpServletRequest throughVite = new MockHttpServletRequest("POST", "/logout");
        throughVite.setServerName("localhost");
        throughVite.setServerPort(5173);

        assertThat(relative.logoutUrl(throughNginx, null)).isEqualTo("http://localhost:8080/");
        assertThat(relative.logoutUrl(throughVite, null)).isEqualTo("http://localhost:5173/");
    }
}
