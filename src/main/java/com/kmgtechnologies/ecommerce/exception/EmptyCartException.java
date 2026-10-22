package com.kmgtechnologies.ecommerce.exception;

public class EmptyCartException extends RuntimeException {

    public EmptyCartException(Long customerId) {
        super("The cart of customer " + customerId + " is empty");
    }
}