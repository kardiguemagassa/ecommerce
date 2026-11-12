package com.kmgtechnologies.ecommerce.dto;

/**
 * Where the signed-in customer stands with a product.
 *
 * @param allowed true once they have a confirmed order of the product
 * @param review  their review, null if they have not written one
 */
public record MyReviewResponse(boolean allowed, ReviewResponse review) {
}
