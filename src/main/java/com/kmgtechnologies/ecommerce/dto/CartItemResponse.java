package com.kmgtechnologies.ecommerce.dto;

import com.kmgtechnologies.ecommerce.entity.CartItem;
import com.kmgtechnologies.ecommerce.media.MediaUrls;

import java.math.BigDecimal;

/**
 * A line of the cart.
 *
 * @param imageUrl public URL of the product image, null without image
 */
public record CartItemResponse(
        Long productId,
        String productName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal subtotal,
        String imageUrl
) {

    public static CartItemResponse from(CartItem item, MediaUrls urls) {
        return new CartItemResponse(
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getProduct().getPrice(),
                item.getQuantity(),
                item.getSubtotal(),
                urls.of(item.getProduct().getImageKey()));
    }
}