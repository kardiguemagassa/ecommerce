package com.kmgtechnologies.ecommerce.dto;

import com.kmgtechnologies.ecommerce.entity.Review;

import java.time.Instant;

/**
 * A review as the shop shows it: every review comes from a confirmed order of the product (a verified purchase).
 *
 * @param author first name and initial of the customer ("Alice M."), never their e-mail
 */
public record ReviewResponse(Long id, int rating, String comment, String author, Instant createdAt, Instant updatedAt) {

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(review.getId(), review.getRating(), review.getComment(),
                author(review.getCustomer().getFullName()), review.getCreatedAt(), review.getUpdatedAt());
    }

    /** "Alice Martin" → "Alice M."; one word stays as it is; no name → "Client". */
    static String author(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "Client";
        }
        String[] words = fullName.strip().split("\\s+");
        if (words.length == 1) {
            return words[0];
        }
        return words[0] + " " + words[words.length - 1].substring(0, 1).toUpperCase() + ".";
    }
}
