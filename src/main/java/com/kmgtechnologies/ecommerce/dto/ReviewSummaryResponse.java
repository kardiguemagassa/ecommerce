package com.kmgtechnologies.ecommerce.dto;

import java.util.List;

/**
 * The rating of a product and how its visible reviews spread over the stars.
 *
 * @param distribution one entry per number of stars, 5 down to 1, zero counts included
 */
public record ReviewSummaryResponse(ProductRatingResponse rating, List<StarCount> distribution) {

    public record StarCount(int stars, long count) {
    }
}
