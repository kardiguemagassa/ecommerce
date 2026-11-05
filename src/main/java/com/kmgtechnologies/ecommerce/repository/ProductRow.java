package com.kmgtechnologies.ecommerce.repository;

import java.math.BigDecimal;

/** A product with its category name, read in one query; the API response adds the public URL of the image. */
public record ProductRow(
        Long id,
        String name,
        String description,
        BigDecimal price,
        int stock,
        Long categoryId,
        String categoryName,
        String imageKey
) {
}
