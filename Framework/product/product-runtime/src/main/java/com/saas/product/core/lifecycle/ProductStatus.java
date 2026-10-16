/**
 * @deprecated This is the pre-1.0 duplicate model (com.saas.product.*).
 *     It is NOT the canonical Product model. The canonical model lives
 *     in tech.kayys.numou.product.domain.* (numou-product-domain).
 *     Do not copy semantics from here; migrate to the Product 1.0 model.
 *     This module is excluded from the Maven reactor and must be deleted
 *     once migration is complete.
 */
package com.saas.product.core.lifecycle;

/**
 * Product lifecycle states.
 *
 * Allowed transitions:
 *
 *   DRAFT ──────► ACTIVE ──────► ARCHIVED
 *     │                              ▲
 *     └──────────────────────────────┘  (can archive from draft directly)
 *
 *   ACTIVE ──────► SUSPENDED (temp unavailable, reactivatable)
 *   SUSPENDED ──► ACTIVE
 *   SUSPENDED ──► ARCHIVED
 */
public enum ProductStatus {

    /**
     * Created but not yet published. Not visible to customers.
     * All editing operations are allowed.
     */
    DRAFT,

    /**
     * Published and available for sale/ordering.
     */
    ACTIVE,

    /**
     * Temporarily hidden from storefront. Retains all data.
     * Can return to ACTIVE.
     */
    SUSPENDED,

    /**
     * Permanently retired. Cannot be ordered. Read-only.
     */
    ARCHIVED;

    public boolean canTransitionTo(ProductStatus target) {
        return switch (this) {
            case DRAFT      -> target == ACTIVE   || target == ARCHIVED;
            case ACTIVE     -> target == SUSPENDED || target == ARCHIVED;
            case SUSPENDED  -> target == ACTIVE   || target == ARCHIVED;
            case ARCHIVED   -> false;  // terminal state
        };
    }
}
