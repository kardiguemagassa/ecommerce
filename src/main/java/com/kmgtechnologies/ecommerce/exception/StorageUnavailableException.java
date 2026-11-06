package com.kmgtechnologies.ecommerce.exception;

/** The media storage could not be reached or refused the operation: nothing was changed in the database. */
public class StorageUnavailableException extends RuntimeException {

    public StorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
