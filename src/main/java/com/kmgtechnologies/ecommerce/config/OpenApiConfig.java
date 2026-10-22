package com.kmgtechnologies.ecommerce.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String PROBLEM_SCHEMA = "ProblemDetail";
    private static final String PROBLEM_JSON = "application/problem+json";

    @Bean
    OpenAPI ecommerceOpenApi(ObjectProvider<BuildProperties> buildProperties) {
        String version = buildProperties.stream()
                .map(BuildProperties::getVersion)
                .findFirst()
                .orElse("dev");

        return new OpenAPI()
                .info(new Info()
                        .title("E-commerce API")
                        .version(version)
                        .description("""
                                Simplified e-commerce backend: product catalog with search and caching,
                                shopping cart, and orders with asynchronous confirmation.
                                Errors follow RFC 9457 (application/problem+json).""")
                        .contact(new Contact()
                                .name("Kardigué Magassa")
                                .url("https://github.com/kardiguemagassa")))
                .components(new Components()
                        .addSchemas(PROBLEM_SCHEMA, problemDetailSchema())
                        .addResponses("BadRequest", problemResponse(
                                "Invalid request: validation errors (detailed in `errors`), malformed body or unsupported parameter"))
                        .addResponses("NotFound", problemResponse("Resource not found"))
                        .addResponses("Conflict", problemResponse(
                                "Business rule violation (duplicate, resource in use, insufficient stock, empty cart) or concurrent modification")));
    }

    private static Schema<?> problemDetailSchema() {
        return new ObjectSchema()
                .description("RFC 9457 problem details")
                .addProperty("type", new StringSchema().example("about:blank"))
                .addProperty("title", new StringSchema().example("Not Found"))
                .addProperty("status", new IntegerSchema().example(404))
                .addProperty("detail", new StringSchema().example("Product with id 42 not found"))
                .addProperty("instance", new StringSchema().example("/api/products/42"))
                .addProperty("errors", new ObjectSchema()
                        .description("Field validation errors, keyed by field name (400 only)")
                        .example(java.util.Map.of("price", "must be greater than 0")));
    }

    private static ApiResponse problemResponse(String description) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(PROBLEM_JSON, new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_SCHEMA))));
    }
}
