package com.kmgtechnologies.ecommerce.dto;

import com.kmgtechnologies.ecommerce.entity.Product;
import com.kmgtechnologies.ecommerce.media.MediaUrls;
import com.kmgtechnologies.ecommerce.repository.ProductRow;

import java.math.BigDecimal;

/**
 * A product of the catalog.
 *
 * @param imageUrl public URL of the product image (JPEG, 4:3), null without image
 */
public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        int stock,
        Long categoryId,
        String categoryName,
        String imageUrl
) {

    public static ProductResponse from(Product product, MediaUrls urls) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                urls.of(product.getImageKey()));
    }

    public static ProductResponse from(ProductRow row, MediaUrls urls) {
        return new ProductResponse(row.id(), row.name(), row.description(), row.price(), row.stock(),
                row.categoryId(), row.categoryName(), urls.of(row.imageKey()));
    }
}
