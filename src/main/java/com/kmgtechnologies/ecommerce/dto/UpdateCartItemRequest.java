package com.kmgtechnologies.ecommerce.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** The new quantity of a cart line (replaces it; removing a line is {@code DELETE}). */
public record UpdateCartItemRequest(
        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be positive")
        Integer quantity
) {
}
