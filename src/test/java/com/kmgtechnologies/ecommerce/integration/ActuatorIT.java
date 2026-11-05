package com.kmgtechnologies.ecommerce.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts real servers (API + management) on random ports to check
 * how Actuator is exposed. Kept apart from @IntegrationTest because
 * it needs a RANDOM_PORT web environment instead of the default MOCK.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ActuatorIT {

    @Value("${local.server.port}")
    private int serverPort;

    @Value("${local.management.port}")
    private int managementPort;

    private final RestClient restClient = RestClient.create();

    @Test
    void health_isUp_onManagementPort() {
        String body = restClient.get()
                .uri("http://localhost:{port}/actuator/health", managementPort)
                .retrieve()
                .body(String.class);

        assertThat(body).contains("\"status\":\"UP\"");
    }

    @Test
    void metrics_registerBusinessAndCacheMeters() {
        String names = restClient.get()
                .uri("http://localhost:{port}/actuator/metrics", managementPort)
                .retrieve()
                .body(String.class);

        assertThat(names)
                .contains("\"ecommerce.orders.placed\"")
                .contains("\"ecommerce.orders.amount\"")
                .contains("\"cache.gets\"");
    }

    @Test
    void metrics_carryTheApplicationTag() {
        String placed = restClient.get()
                .uri("http://localhost:{port}/actuator/metrics/ecommerce.orders.placed", managementPort)
                .retrieve()
                .body(String.class);

        assertThat(placed).contains("\"tag\":\"application\"").contains("\"ecommerce\"");
    }

    @Test
    void prometheusEndpoint_isGone_metricsArePushedOverOtlp() {
        ResponseEntity<Void> response = restClient.get()
                .uri("http://localhost:{port}/actuator/prometheus", managementPort)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> { })
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void actuator_isNotExposed_onPublicApiPort() {
        ResponseEntity<Void> response = restClient.get()
                .uri("http://localhost:{port}/actuator/health", serverPort)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> { })
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
