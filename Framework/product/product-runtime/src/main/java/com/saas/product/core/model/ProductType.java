/**
 * @deprecated This is the pre-1.0 duplicate model (com.saas.product.*).
 *     It is NOT the canonical Product model. The canonical model lives
 *     in tech.kayys.numou.product.domain.* (numou-product-domain).
 *     Do not copy semantics from here; migrate to the Product 1.0 model.
 *     This module is excluded from the Maven reactor and must be deleted
 *     once migration is complete.
 */
package com.saas.product.core.model;

/**
 * Universal product type classification.
 * Determines which lifecycle rules and extension contexts are applicable.
 */
public enum ProductType {

    /**
     * Tangible item with physical inventory (e.g. clothing, electronics, groceries).
     */
    PHYSICAL,

    /**
     * Digital goods: software, ebooks, media files. No physical stock.
     */
    DIGITAL,

    /**
     * Time-based recurring access (SaaS, streaming, membership).
     */
    SUBSCRIPTION,

    /**
     * One-time or recurring service delivery (cleaning, repair, consulting).
     */
    SERVICE,

    /**
     * Redeemable voucher or gift card with monetary value.
     */
    VOUCHER,

    /**
     * Bundle of other products sold together (combo meal, starter kit).
     * Contains a list of component ProductIds.
     */
    BUNDLE,

    /**
     * Master product with multiple variants (size/color SKUs).
     * The aggregate itself is not orderable; variants are.
     */
    VARIANT_PARENT,

    /**
     * Concrete variant of a VARIANT_PARENT.
     */
    VARIANT
}
