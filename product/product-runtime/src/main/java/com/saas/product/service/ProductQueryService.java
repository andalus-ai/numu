package com.saas.product.service;

import com.saas.product.core.ProductAggregate;
import com.saas.product.core.lifecycle.ProductStatus;
import com.saas.product.spi.ProductQuery;
import com.saas.product.spi.ProductQuery.ProductPage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Read-side service.
 *
 * Kept separate from ProductService (write side) following CQRS.
 * No @Transactional here — reads can use read replicas / caches.
 *
 * Future: swap ProductQuery impl for an Elasticsearch-backed one
 * without touching this service.
 */
@ApplicationScoped
public class ProductQueryService {

    @Inject
    ProductQuery query;

    public ProductPage list(String tenantId, int page, int size) {
        return query.findAll(tenantId, page, size);
    }

    public ProductPage listActive(String tenantId, int page, int size) {
        return query.findByStatus(tenantId, ProductStatus.ACTIVE, page, size);
    }

    public ProductPage listByCategory(String tenantId, String categoryId, int page, int size) {
        return query.findByCategory(tenantId, categoryId, page, size);
    }

    public ProductPage listByContext(String tenantId, String context, int page, int size) {
        return query.findByContext(tenantId, context, page, size);
    }

    public ProductPage search(String tenantId, String query, int page, int size) {
        return this.query.search(tenantId, query, page, size);
    }

    public List<ProductAggregate> scanBySku(String tenantId, String skuPrefix, int limit) {
        return query.findBySkuPrefix(tenantId, skuPrefix, limit);
    }
}
