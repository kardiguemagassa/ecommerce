package com.kmgtechnologies.ecommerce.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * For {@code @WebMvcTest} slices importing {@link SecurityConfig}: the Keycloak registration of the BFF login, as the
 * application declares it, without the OAuth2 client auto-configuration of a full context.
 */
@TestConfiguration(proxyBeanMethods = false)
public class BffTestConfiguration {

    static final String REALM = "http://localhost:8180/realms/ecommerce";

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
        return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("keycloak")
                .clientId("ecommerce-bff")
                .clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri(REALM + "/protocol/openid-connect/auth")
                .tokenUri(REALM + "/protocol/openid-connect/token")
                .jwkSetUri(REALM + "/protocol/openid-connect/certs")
                .userNameAttributeName("sub")
                .build());
    }
}
