package com.kmgtechnologies.ecommerce.dto;

import com.kmgtechnologies.ecommerce.entity.Product;
import com.kmgtechnologies.ecommerce.entity.ProductImage;
import com.kmgtechnologies.ecommerce.media.MediaUrls;
import com.kmgtechnologies.ecommerce.repository.ProductRow;

import java.math.BigDecimal;
import java.util.List;

/**
 * A product of the catalog.
 *
 * @param imageUrl public URL of the main image (JPEG within 1200 x 900, proportions kept), null without image
 * @param images   the views of the product, main image first, 4 at most; in the product details and the answers of the
 *                 changes only (empty in search results, which show the main image)
 * @param rating   average and count of the visible reviews (in search results too: the catalogue cards show the stars)
 */
public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        int stock,
        Long categoryId,
        String categoryName,
        String imageUrl,
        List<ProductImageResponse> images,
        ProductRatingResponse rating
) {

    public static ProductResponse from(Product product, ProductRatingResponse rating, MediaUrls urls) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                urls.of(product.getImageKey()),
                views(product.getImages(), urls),
                rating);
    }

    /** A product of a search result: its main image only. */
    public static ProductResponse from(ProductRow row, MediaUrls urls) {
        return from(row, List.of(), urls);
    }

    /** The product details: the row and its views, main image first. */
    public static ProductResponse from(ProductRow row, List<ProductImage> images, MediaUrls urls) {
        return new ProductResponse(row.id(), row.name(), row.description(), row.price(), row.stock(),
                row.categoryId(), row.categoryName(), urls.of(row.imageKey()), views(images, urls),
                ProductRatingResponse.of(row.ratingAverage(), row.reviewCount()));
    }

    private static List<ProductImageResponse> views(List<ProductImage> images, MediaUrls urls) {
        return images.stream().map(image -> ProductImageResponse.from(image, urls)).toList();
    }
}
