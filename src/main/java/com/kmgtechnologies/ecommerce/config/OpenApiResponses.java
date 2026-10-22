package com.kmgtechnologies.ecommerce.config;

/**
 * References to the reusable error responses declared in {@link OpenApiConfig}.
 * Used in @ApiResponse(ref = ...) so every endpoint shares the same RFC 9457 error contract.
 */
public final class OpenApiResponses {

    public static final String BAD_REQUEST = "#/components/responses/BadRequest";
    public static final String NOT_FOUND = "#/components/responses/NotFound";
    public static final String CONFLICT = "#/components/responses/Conflict";

    private OpenApiResponses() {
    }
}
