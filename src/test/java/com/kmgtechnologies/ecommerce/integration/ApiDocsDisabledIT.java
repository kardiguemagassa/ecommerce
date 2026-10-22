package com.kmgtechnologies.ecommerce.integration;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Production setting: API_DOCS_ENABLED=false must remove both the OpenAPI document and Swagger UI.
 * Uses the environment variable itself (not the springdoc properties) to test the real wiring.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "API_DOCS_ENABLED=false")
@ActiveProfiles("test")
class ApiDocsDisabledIT {

    @Value("${local.server.port}")
    private int port;

    @ParameterizedTest
    @ValueSource(strings = {"/v3/api-docs", "/swagger-ui.html"})
    void documentationIsNotExposed(String path) {
        HttpStatusCode status = RestClient.create().get()
                .uri("http://localhost:" + port + path)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> { })
                .toBodilessEntity()
                .getStatusCode();

        assertThat(status).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
