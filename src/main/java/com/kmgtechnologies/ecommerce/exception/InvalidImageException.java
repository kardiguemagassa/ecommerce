package com.kmgtechnologies.ecommerce.exception;

/** The upload claims a supported format but cannot be used: empty, damaged, or too many pixels to decode safely. */
public class InvalidImageException extends RuntimeException {

    public InvalidImageException(String message) {
        super(message);
    }
}
