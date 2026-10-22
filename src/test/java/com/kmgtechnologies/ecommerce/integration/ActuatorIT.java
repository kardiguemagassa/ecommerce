package com.kmgtechnologies.ecommerce.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts real servers (API + management) on random ports to check
 * how Actuator is exposed. Kept apart from @IntegrationTest because
 * it needs a RANDOM_PORT web environment instead of the default MOCK.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.prometheus.metrics.export.enabled=true")
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
    void prometheus_exposesBusinessAndCacheMetrics_withApplicationTag() {
        String body = restClient.get()
                .uri("http://localhost:{port}/actuator/prometheus", managementPort)
                .accept(MediaType.TEXT_PLAIN)
                .retrieve()
                .body(String.class);

        assertThat(body)
                .contains("ecommerce_orders_placed_total{application=\"ecommerce\"}")
                .contains("ecommerce_orders_amount_count")
                .contains("cache_gets_total")
                .contains("name=\"products\"");
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
