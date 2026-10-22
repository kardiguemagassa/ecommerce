package com.kmgtechnologies.ecommerce.integration;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the published OpenAPI contract: metadata, tags, shared RFC 9457 error responses.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApiDocsIT {

    @Value("${local.server.port}")
    private int port;

    private DocumentContext apiDocs;

    @BeforeEach
    void loadApiDocs() {
        String json = RestClient.create().get()
                .uri("http://localhost:{port}/v3/api-docs", port)
                .retrieve()
                .body(String.class);
        apiDocs = JsonPath.parse(json);
    }

    @Test
    void exposesOpenApi30_withApplicationMetadata() {
        assertThat(apiDocs.read("$.openapi", String.class)).startsWith("3.0");
        assertThat(apiDocs.read("$.info.title", String.class)).isEqualTo("E-commerce API");
    }

    @Test
    void groupsEndpointsUnderTheFiveBusinessTags() {
        List<String> tags = apiDocs.read("$.tags[*].name");
        assertThat(tags).containsExactlyInAnyOrder("Cart", "Categories", "Customers", "Orders", "Products");
    }

    @Test
    void documentsOnlyBusinessPaths() {
        Map<String, Object> paths = apiDocs.read("$.paths");
        assertThat(paths.keySet())
                .isNotEmpty()
                .allMatch(path -> path.startsWith("/api/"));
    }

    @Test
    void errorResponsesReferenceTheSharedProblemDetailContract() {
        Map<String, Object> problemProperties = apiDocs.read("$.components.schemas.ProblemDetail.properties");
        assertThat(problemProperties).containsKeys("type", "title", "status", "detail", "instance", "errors");

        Map<String, Object> notFoundContent = apiDocs.read("$.components.responses.NotFound.content");
        assertThat(notFoundContent).containsKey("application/problem+json");

        String productNotFoundRef = apiDocs.read("$.paths['/api/products/{id}'].get.responses['404']['$ref']");
        assertThat(productNotFoundRef).isEqualTo("#/components/responses/NotFound");
    }
}
