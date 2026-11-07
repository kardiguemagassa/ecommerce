package com.kmgtechnologies.ecommerce.dto;

import com.kmgtechnologies.ecommerce.entity.ProductImage;
import com.kmgtechnologies.ecommerce.media.MediaUrls;

/**
 * A view of a product.
 *
 * @param id  identifier of the view, to remove it or to change the order of the views
 * @param url public URL of the image (JPEG within 1200 x 900, proportions kept)
 */
public record ProductImageResponse(Long id, String url) {

    public static ProductImageResponse from(ProductImage image, MediaUrls urls) {
        return new ProductImageResponse(image.getId(), urls.of(image.getImageKey()));
    }
}
