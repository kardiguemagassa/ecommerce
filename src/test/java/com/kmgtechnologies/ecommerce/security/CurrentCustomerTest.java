package com.kmgtechnologies.ecommerce.security;

import com.kmgtechnologies.ecommerce.dto.CustomerResponse;
import com.kmgtechnologies.ecommerce.service.CustomerIdentity;
import com.kmgtechnologies.ecommerce.service.CustomerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentCustomerTest {

    private static final String SUBJECT = "6f1c2a3b-0000-4000-8000-000000000001";
    private static final CustomerResponse ALICE = new CustomerResponse(1L, "alice@example.com", "Alice Martin");

    private final CustomerService customerService = mock(CustomerService.class);
    private final CurrentCustomer currentCustomer = new CurrentCustomer(customerService);

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private static void signIn(Map<String, Object> claims) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claims(all -> all.putAll(claims))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @Test
    void identityComesFromTheClaimsOfTheToken() {
        signIn(Map.of("sub", SUBJECT, "email", "alice@example.com", "email_verified", true, "name", "Alice Martin"));
        when(customerService.provision(new CustomerIdentity(SUBJECT, "alice@example.com", true, "Alice Martin")))
                .thenReturn(ALICE);

        assertThat(currentCustomer.id()).isEqualTo(1L);
    }

    @Test
    void userWithoutNameIsNamedAfterTheirEmail_andUnverifiedByDefault() {
        signIn(Map.of("sub", SUBJECT, "email", "alice@example.com"));
        when(customerService.provision(new CustomerIdentity(SUBJECT, "alice@example.com", false, "alice@example.com")))
                .thenReturn(ALICE);

        assertThat(currentCustomer.profile()).isEqualTo(ALICE);
    }

    @Test
    void tokenWithoutEmailIsRefused() {
        signIn(Map.of("sub", SUBJECT));

        assertThatThrownBy(currentCustomer::id).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void withoutSignedInUserThereIsNoCustomer() {
        assertThatThrownBy(currentCustomer::id).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void concurrentFirstCallsReadTheCustomerCreatedByTheOtherOne() {
        signIn(Map.of("sub", SUBJECT, "email", "alice@example.com", "email_verified", true));
        when(customerService.provision(any(CustomerIdentity.class)))
                .thenThrow(new DataIntegrityViolationException("uk_customers_external_id"));
        when(customerService.findByExternalId(SUBJECT)).thenReturn(ALICE);

        assertThat(currentCustomer.id()).isEqualTo(1L);
    }
}
