package com.kmgtechnologies.ecommerce.exception;

/** Only a customer with a confirmed order of the product reviews it. */
public class ReviewNotAllowedException extends RuntimeException {

    public ReviewNotAllowedException() {
        super("Only a customer who ordered this product can review it");
    }
}
