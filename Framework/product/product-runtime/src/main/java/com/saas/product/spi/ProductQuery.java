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

import java.util.List;

/**
 * Read-side query interface (CQRS separation).
 *
 * Kept separate from ProductRepository (write side) to allow:
 *  - Different implementations (e.g. Elasticsearch for search)
 *  - Independent scaling of read vs write paths
 *  - Projections without loading full aggregates
 *
 * All queries are scoped to a tenant.
 */
public interface ProductQuery {

    /**
     * Page through products for a tenant.
     */
    ProductPage findAll(String tenantId, int page, int size);

    /**
     * Filter by lifecycle status.
     */
    ProductPage findByStatus(String tenantId, ProductStatus status, int page, int size);

    /**
     * Filter by category.
     */
    ProductPage findByCategory(String tenantId, String categoryId, int page, int size);

    /**
     * Find products that have a specific extension context attached.
     */
    ProductPage findByContext(String tenantId, String context, int page, int size);

    /**
     * Full-text search across name, sku, description.
     */
    ProductPage search(String tenantId, String query, int page, int size);

    /**
     * Find products whose SKU starts with a given prefix (useful for barcode scan).
     */
    List<ProductAggregate> findBySkuPrefix(String tenantId, String skuPrefix, int limit);

    /**
     * Paginated result wrapper.
     */
    record ProductPage(
            List<ProductAggregate> items,
            long totalCount,
            int page,
            int size) {

        public int totalPages() {
            return size == 0 ? 0 : (int) Math.ceil((double) totalCount / size);
        }

        public boolean hasNext() {
            return page < totalPages() - 1;
        }
    }
}
