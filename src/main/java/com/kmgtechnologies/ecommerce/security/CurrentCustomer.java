package com.kmgtechnologies.ecommerce.security;

import com.kmgtechnologies.ecommerce.dto.CustomerResponse;
import com.kmgtechnologies.ecommerce.service.CustomerIdentity;
import com.kmgtechnologies.ecommerce.service.CustomerService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * The customer of the current request, taken from the authenticated user: a bearer token today, an OpenID Connect
 * login of the BFF tomorrow (both expose the same claims). The record is created on the first call and its e-mail
 * and name follow Keycloak afterwards: there is no sign-up endpoint in the API.
 */
@Component
public class CurrentCustomer {

    private final CustomerService customerService;

    public CurrentCustomer(CustomerService customerService) {
        this.customerService = customerService;
    }

    public Long id() {
        return profile().id();
    }

    public CustomerResponse profile() {
        CustomerIdentity identity = identity(SecurityContextHolder.getContext().getAuthentication());
        try {
            return customerService.provision(identity);
        } catch (DataIntegrityViolationException concurrentFirstCall) {
            // Two first requests of the same user at once: the other one created the record.
            return customerService.findByExternalId(identity.subject());
        }
    }

    static CustomerIdentity identity(Authentication authentication) {
        Map<String, Object> claims;
        if (authentication instanceof JwtAuthenticationToken token) {
            claims = token.getTokenAttributes();
        } else if (authentication != null && authentication.getPrincipal() instanceof OAuth2AuthenticatedPrincipal user) {
            claims = user.getAttributes();
        } else {
            throw new AuthenticationCredentialsNotFoundException("No signed-in user");
        }
        String subject = string(claims, "sub");
        String email = string(claims, "email");
        if (subject == null || email == null) {
            throw new AccessDeniedException("The token carries no subject or no e-mail");
        }
        String name = string(claims, "name");
        return new CustomerIdentity(subject, email, Boolean.TRUE.equals(claims.get("email_verified")),
                name == null || name.isBlank() ? email : name);
    }

    private static String string(Map<String, Object> claims, String name) {
        return claims.get(name) instanceof String value && !value.isBlank() ? value : null;
    }
}
