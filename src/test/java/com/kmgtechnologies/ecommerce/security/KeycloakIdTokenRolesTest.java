package com.kmgtechnologies.ecommerce.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakIdTokenRolesTest {

    private final KeycloakIdTokenRoles mapper = new KeycloakIdTokenRoles();

    private static OidcUserAuthority user(Map<String, Object> claims) {
        OidcIdToken idToken = new OidcIdToken("id-token", Instant.now(), Instant.now().plusSeconds(300), claims);
        return new OidcUserAuthority(idToken);
    }

    @Test
    void realmRolesOfTheIdTokenBecomeRoleAuthorities_nextToTheOriginalOnes() {
        OidcUserAuthority oidc = user(Map.of("sub", "alice", "roles", List.of("CUSTOMER", "ADMIN")));

        List<String> authorities = mapper.mapAuthorities(List.of(oidc, new SimpleGrantedAuthority("SCOPE_openid")))
                .stream().map(GrantedAuthority::getAuthority).toList();

        assertThat(authorities).contains("OIDC_USER", "SCOPE_openid", "ROLE_CUSTOMER", "ROLE_ADMIN");
    }

    @Test
    void idTokenWithoutRolesGrantsNoRole() {
        List<String> authorities = mapper.mapAuthorities(List.of(user(Map.of("sub", "alice"))))
                .stream().map(GrantedAuthority::getAuthority).toList();

        assertThat(authorities).noneMatch(authority -> authority.startsWith("ROLE_"));
    }
}
