package com.kmgtechnologies.ecommerce.exception;

/** The upload is not a JPEG, PNG or WebP image, whatever its name or declared type says. */
public class UnsupportedImageException extends RuntimeException {

    public UnsupportedImageException() {
        super("Only JPEG, PNG and WebP images are accepted");
    }
}
