package com.kmgtechnologies.ecommerce.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Browser logins (BFF): the realm roles of the user, from the {@code roles} claim that the {@code ecommerce-bff}
 * client puts in the ID token, become {@code ROLE_*} authorities, so the same rules apply to sessions and to bearer
 * tokens ({@link KeycloakRealmRoles}). The authorities granted by Spring Security ({@code OIDC_USER}, scopes) are kept.
 */
public final class KeycloakIdTokenRoles implements GrantedAuthoritiesMapper {

    static final String ROLES_CLAIM = "roles";

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
        Set<GrantedAuthority> mapped = new LinkedHashSet<>(authorities);
        for (GrantedAuthority authority : authorities) {
            if (authority instanceof OidcUserAuthority oidc) {
                List<String> roles = oidc.getIdToken().getClaimAsStringList(ROLES_CLAIM);
                if (roles != null) {
                    roles.forEach(role -> mapped.add(new SimpleGrantedAuthority("ROLE_" + role)));
                }
            }
        }
        return mapped;
    }
}
