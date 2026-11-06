package com.kmgtechnologies.ecommerce.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

import java.net.URI;

/**
 * Backend for Frontend settings: where the browser lands after login and logout, and the Keycloak end-session endpoint
 * (RP-initiated logout) and account console, as the browser reaches them.
 *
 * @param frontUrl      the front-end; relative by default ({@code /}): resolved against the address the browser used,
 *                      so the same API serves nginx (http://localhost:8080) and the Vite dev server
 *                      (http://localhost:5173). Absolute when the front-end lives on another origin.
 * @param endSessionUri Keycloak end-session endpoint
 * @param accountUri    Keycloak account console, where customers change their name, e-mail and password
 */
@ConfigurationProperties("app.bff")
public record BffProperties(URI frontUrl, URI endSessionUri, URI accountUri) {

    public BffProperties {
        Assert.notNull(frontUrl, "app.bff.front-url is required");
        Assert.notNull(endSessionUri, "app.bff.end-session-uri is required");
        Assert.notNull(accountUri, "app.bff.account-uri is required");
    }
}
