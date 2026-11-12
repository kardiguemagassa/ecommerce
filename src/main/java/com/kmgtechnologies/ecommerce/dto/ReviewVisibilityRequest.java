package com.kmgtechnologies.ecommerce.dto;

import jakarta.validation.constraints.NotNull;

/** Hides a review from the shop (abusive, off topic) or shows it again; it is never edited nor deleted by moderation. */
public record ReviewVisibilityRequest(
        @NotNull(message = "Hidden is required")
        Boolean hidden
) {
}
