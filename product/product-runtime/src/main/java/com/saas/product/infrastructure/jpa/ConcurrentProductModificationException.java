package com.saas.product.infrastructure.jpa;

import com.saas.product.core.model.ProductId;

/**
 * Thrown when an optimistic lock conflict is detected during save.
 * Maps to HTTP 409 Conflict at the REST layer.
 */
public class ConcurrentProductModificationException extends RuntimeException {

    private final ProductId productId;

    public ConcurrentProductModificationException(ProductId productId, Throwable cause) {
        super("Concurrent modification detected for product: " + productId, cause);
        this.productId = productId;
    }

    public ProductId getProductId() { return productId; }
}
