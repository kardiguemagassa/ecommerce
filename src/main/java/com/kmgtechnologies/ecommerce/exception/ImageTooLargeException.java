package com.kmgtechnologies.ecommerce.exception;

/** The upload weighs more than allowed for its use (product image or avatar). */
public class ImageTooLargeException extends RuntimeException {

    public ImageTooLargeException(long maxBytes) {
        super("The image must not exceed " + (maxBytes / (1024 * 1024)) + " MB");
    }
}
