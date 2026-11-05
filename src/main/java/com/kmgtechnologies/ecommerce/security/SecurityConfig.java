package com.kmgtechnologies.ecommerce.security;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Two ways in, one set of rules.
 * <ul>
 *   <li><b>Bearer token</b> (Swagger UI, curl, other services, tests): the API is an OAuth2 resource server. Keycloak
 *       access tokens validated with the keys, issuer and audience under
 *       {@code spring.security.oauth2.resourceserver.jwt}; no session, no CSRF check.</li>
 *   <li><b>Browser session</b> (Backend for Frontend): {@code /oauth2/authorization/keycloak} signs in with Keycloak
 *       (authorization code with PKCE, confidential client {@code ecommerce-bff}); the browser then only holds an
 *       HttpOnly session cookie, the tokens stay on the server. Writes made with the cookie need the CSRF token
 *       ({@code XSRF-TOKEN} cookie sent back as {@code X-XSRF-TOKEN}); {@code POST /logout} also ends the Keycloak
 *       session.</li>
 * </ul>
 * Rules:
 * <ul>
 *   <li>Catalog reads are public; catalog writes and {@code /api/admin/**} need the {@code ADMIN} realm role.</li>
 *   <li>{@code /api/me/**}: the cart, orders and profile of the signed-in customer ({@code CUSTOMER} role); the
 *       customer is always the one of the token or session, never an id taken from the URL.</li>
 *   <li>Anything else is refused: a new endpoint is closed until a rule opens it.</li>
 * </ul>
 * Every refusal is an RFC 9457 problem ({@link SecurityProblemAdvice}): no redirect to a login page, the front-end
 * decides.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BffProperties.class)
public class SecurityConfig {

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoles());
        return converter;
    }

    /** Picked up by {@code oauth2Login}: realm roles of the ID token for browser sessions. */
    @Bean
    KeycloakIdTokenRoles keycloakIdTokenRoles() {
        return new KeycloakIdTokenRoles();
    }

    @Bean
    SecurityProblemHandler securityProblemHandler(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        return new SecurityProblemHandler(resolver);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProblemHandler problems,
                                            JwtAuthenticationConverter jwtAuthenticationConverter,
                                            ClientRegistrationRepository registrations,
                                            BffProperties bff) throws Exception {
        DefaultOAuth2AuthorizationRequestResolver authorizationRequests =
                new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
        // PKCE for the confidential client too (OAuth 2.1, and required by the Keycloak client).
        authorizationRequests.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());

        return http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        // Plain token, as a single-page application reads it from the cookie and sends it back.
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .requireCsrfProtectionMatcher(new SessionCsrfMatcher()))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .oauth2Login(login -> login
                        .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(authorizationRequests))
                        .successHandler(successHandler(bff))
                        .failureHandler(new SimpleUrlAuthenticationFailureHandler(bff.frontUrl() + "?login=failed")))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler(new KeycloakLogoutSuccessHandler(bff)))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                        // Catalog: anyone can browse, administrators maintain it.
                        .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**",
                                "/api/categories", "/api/categories/**").permitAll()
                        .requestMatchers("/api/products", "/api/products/**",
                                "/api/categories", "/api/categories/**").hasRole("ADMIN")
                        // Session state for the front-end, signed in or not.
                        .requestMatchers(HttpMethod.GET, "/api/me/session").permitAll()
                        .requestMatchers("/api/me", "/api/me/**").hasRole("CUSTOMER")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Management port only (8081, never published beyond localhost); 404 on the API port.
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**",
                                "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .anyRequest().denyAll())
                .build();
    }

    private static SimpleUrlAuthenticationSuccessHandler successHandler(BffProperties bff) {
        SimpleUrlAuthenticationSuccessHandler handler = new SimpleUrlAuthenticationSuccessHandler(bff.frontUrl().toString());
        handler.setAlwaysUseDefaultTargetUrl(true);
        return handler;
    }
}
