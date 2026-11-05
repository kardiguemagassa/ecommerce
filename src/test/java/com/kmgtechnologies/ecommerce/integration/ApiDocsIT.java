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
    void groupsEndpointsUnderTheBusinessTagsAndTheSession() {
        List<String> tags = apiDocs.read("$.tags[*].name");
        assertThat(tags).containsExactlyInAnyOrder("Cart", "Categories", "Customers", "Orders", "Products", "Session");
    }

    @Test
    void swaggerUiSignsInWithKeycloak() {
        assertThat(apiDocs.read("$.components.securitySchemes.keycloak.type", String.class)).isEqualTo("oauth2");
        assertThat(apiDocs.read("$.components.securitySchemes.keycloak.flows.authorizationCode.authorizationUrl",
                String.class)).isEqualTo("http://localhost:8180/realms/ecommerce/protocol/openid-connect/auth");
        List<Object> requirements = apiDocs.read("$.security[*].keycloak");
        assertThat(requirements).hasSize(1);
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

        String imageRejectedRef = apiDocs.read("$.paths['/api/products/{id}/image'].put.responses['415']['$ref']");
        assertThat(imageRejectedRef).isEqualTo("#/components/responses/ImageRejected");
        assertThat(problemProperties).containsKeys("code", "traceId");
    }

    @Test
    void imageUploadsAreMultipart() {
        Map<String, Object> productImage = apiDocs.read("$.paths['/api/products/{id}/image'].put.requestBody.content");
        Map<String, Object> avatar = apiDocs.read("$.paths['/api/me/avatar'].put.requestBody.content");
        assertThat(productImage).containsOnlyKeys("multipart/form-data");
        assertThat(avatar).containsOnlyKeys("multipart/form-data");
    }
}
