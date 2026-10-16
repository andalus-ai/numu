/**
 * @deprecated This is the pre-1.0 duplicate model (com.saas.product.*).
 *     It is NOT the canonical Product model. The canonical model lives
 *     in tech.kayys.numou.product.domain.* (numou-product-domain).
 *     Do not copy semantics from here; migrate to the Product 1.0 model.
 *     This module is excluded from the Maven reactor and must be deleted
 *     once migration is complete.
 */
package com.saas.product.spi;

import com.saas.product.core.ProductAggregate;
import com.saas.product.core.lifecycle.ProductStatus;
import com.saas.product.core.model.ProductId;

import java.util.List;
import java.util.Optional;

/**
 * Storage-agnostic repository contract for ProductAggregate.
 *
 * Implementations may use:
 *  - Quarkus Panache (JPA/PostgreSQL)  → PanacheProductRepository
 *  - MongoDB Panache                    → MongoProductRepository
 *  - In-memory (tests)                 → InMemoryProductRepository
 *  - Event store                        → EventSourcedProductRepository
 *
 * Every method is tenant-scoped; implementations MUST enforce tenantId isolation.
 */
public interface ProductRepository {

    /**
     * Persist a new or updated aggregate.
     * Implementations should enforce optimistic locking via aggregate.getVersion().
     */
    ProductAggregate save(ProductAggregate product);

    /**
     * Load aggregate by ID, scoped to tenant.
     */
    Optional<ProductAggregate> findById(ProductId id, String tenantId);

    /**
     * Load by SKU within tenant scope.
     */
    Optional<ProductAggregate> findBySku(String sku, String tenantId);

    /**
     * Delete (hard delete). In production prefer archive() lifecycle transition.
     */
    void delete(ProductId id, String tenantId);

    /**
     * Check existence without loading the full aggregate.
     */
    boolean exists(ProductId id, String tenantId);
}
