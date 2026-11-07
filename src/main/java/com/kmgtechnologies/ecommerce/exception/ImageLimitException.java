package com.kmgtechnologies.ecommerce.exception;

/** The product already shows as many views as allowed. */
public class ImageLimitException extends RuntimeException {

    public ImageLimitException(int max) {
        super("A product has at most " + max + " images");
    }
}
