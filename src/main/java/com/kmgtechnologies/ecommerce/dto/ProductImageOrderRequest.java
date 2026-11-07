package com.kmgtechnologies.ecommerce.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** The views of a product in their new order, each one exactly once; the first becomes the main image. */
public record ProductImageOrderRequest(
        @NotEmpty(message = "Image ids are required")
        List<@NotNull(message = "Image ids are required") Long> imageIds
) {
}
