package com.kmgtechnologies.ecommerce.dto;

/**
 * Where the signed-in customer stands with a product.
 *
 * @param allowed true once they have a confirmed order of the product
 * @param review  their review, null if they have not written one
 * @param hidden  true when the moderation hid their review from the shop (they still see and may change it)
 */
public record MyReviewResponse(boolean allowed, ReviewResponse review, boolean hidden) {
}
