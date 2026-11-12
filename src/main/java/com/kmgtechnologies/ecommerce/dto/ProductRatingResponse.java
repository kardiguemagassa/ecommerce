package com.kmgtechnologies.ecommerce.dto;

/**
 * The visible reviews of a product, summed up.
 *
 * @param average mean of the ratings, one decimal (4.3), null without review
 * @param count   number of visible reviews
 */
public record ProductRatingResponse(Double average, long count) {

    public static final ProductRatingResponse NONE = new ProductRatingResponse(null, 0);

    /** From the aggregates of a query: AVG (null without row) and COUNT. */
    public static ProductRatingResponse of(Double average, Long count) {
        if (count == null || count == 0 || average == null) {
            return NONE;
        }
        return new ProductRatingResponse(Math.round(average * 10) / 10.0, count);
    }
}
