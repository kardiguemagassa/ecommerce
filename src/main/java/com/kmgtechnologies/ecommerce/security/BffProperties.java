package com.kmgtechnologies.ecommerce.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

import java.net.URI;

/**
 * Backend for Frontend settings: where the browser lands after login and logout, and the Keycloak end-session endpoint
 * (RP-initiated logout), both as the browser reaches them.
 */
@ConfigurationProperties("app.bff")
public record BffProperties(URI frontUrl, URI endSessionUri) {

    public BffProperties {
        Assert.notNull(frontUrl, "app.bff.front-url is required");
        Assert.notNull(endSessionUri, "app.bff.end-session-uri is required");
    }
}
