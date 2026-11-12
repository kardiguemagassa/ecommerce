package com.kmgtechnologies.ecommerce.dto;

import com.kmgtechnologies.ecommerce.entity.Review;

import java.time.Instant;

/** A review as the moderation sees it: its product, its author in full (support may contact them) and its visibility. */
public record AdminReviewResponse(
        Long id,
        Long productId,
        String productName,
        String authorName,
        String authorEmail,
        int rating,
        String comment,
        boolean hidden,
        Instant createdAt
) {

    public static AdminReviewResponse from(Review review) {
        return new AdminReviewResponse(review.getId(), review.getProduct().getId(), review.getProduct().getName(),
                review.getCustomer().getFullName(), review.getCustomer().getEmail(), review.getRating(),
                review.getComment(), review.isHidden(), review.getCreatedAt());
    }
}
