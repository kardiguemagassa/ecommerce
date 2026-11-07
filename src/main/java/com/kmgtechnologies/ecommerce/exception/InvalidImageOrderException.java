package com.kmgtechnologies.ecommerce.exception;

/** A new order of the views that does not list each view of the product exactly once. */
public class InvalidImageOrderException extends RuntimeException {

    public InvalidImageOrderException() {
        super("The new order must list each image of the product exactly once");
    }
}
