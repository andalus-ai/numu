package com.saas.product.service;

/**
 * Thrown when creating a product with a SKU already in use within the same tenant.
 */
public class DuplicateSkuException extends RuntimeException {

    private final String sku;
    private final String tenantId;

    public DuplicateSkuException(String sku, String tenantId) {
        super("SKU already exists: sku=" + sku + ", tenant=" + tenantId);
        this.sku      = sku;
        this.tenantId = tenantId;
    }

    public String getSku()      { return sku; }
    public String getTenantId() { return tenantId; }
}
