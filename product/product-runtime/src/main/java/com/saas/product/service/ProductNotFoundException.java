package com.saas.product.service;

import com.saas.product.core.model.ProductId;

/**
 * Thrown when a requested product does not exist for the given tenant.
 */
public class ProductNotFoundException extends RuntimeException {

    private final ProductId productId;
    private final String tenantId;

    public ProductNotFoundException(ProductId productId, String tenantId) {
        super("Product not found: id=" + productId + ", tenant=" + tenantId);
        this.productId = productId;
        this.tenantId  = tenantId;
    }

    public ProductId getProductId() { return productId; }
    public String getTenantId()     { return tenantId; }
}
