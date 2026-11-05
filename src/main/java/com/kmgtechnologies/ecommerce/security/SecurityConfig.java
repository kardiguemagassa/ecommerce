package com.kmgtechnologies.ecommerce.security;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * The API as an OAuth2 resource server: Keycloak access tokens ({@code Authorization: Bearer}), validated with the
 * keys, issuer and audience configured under {@code spring.security.oauth2.resourceserver.jwt}.
 * <ul>
 *   <li>Catalog reads are public; catalog writes and {@code /api/admin/**} need the {@code ADMIN} realm role.</li>
 *   <li>{@code /api/me/**}: the cart, orders and profile of the signed-in customer ({@code CUSTOMER} role); the
 *       customer is always the one of the token, never an id taken from the URL.</li>
 *   <li>Anything else is refused: a new endpoint is closed until a rule opens it.</li>
 * </ul>
 * Stateless: no session and no cookie, hence no CSRF protection (a bearer token is never sent automatically).
 * Refusals are RFC 9457 problems ({@link SecurityProblemAdvice}).
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoles());
        return converter;
    }

    @Bean
    SecurityProblemHandler securityProblemHandler(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        return new SecurityProblemHandler(resolver);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProblemHandler problems,
                                            JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                        // Catalog: anyone can browse, administrators maintain it.
                        .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**",
                                "/api/categories", "/api/categories/**").permitAll()
                        .requestMatchers("/api/products", "/api/products/**",
                                "/api/categories", "/api/categories/**").hasRole("ADMIN")
                        .requestMatchers("/api/me", "/api/me/**").hasRole("CUSTOMER")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Management port only (8081, never published beyond localhost); 404 on the API port.
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**",
                                "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .anyRequest().denyAll())
                .build();
    }
}
