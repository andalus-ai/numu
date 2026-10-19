package com.saas.product.core;

/**
 * Thrown when a lifecycle state transition is illegal.
 */
public class ProductLifecycleException extends RuntimeException {

    public ProductLifecycleException(String message) {
        super(message);
    }

    public ProductLifecycleException(String message, Throwable cause) {
        super(message, cause);
    }
}
