package tech.kayys.syirkah.product.domain.variant;

/**
 * Variant lifecycle - independent from the product lifecycle: a
 * product can stay ACTIVE while one variant is already
 * DISCONTINUED.
 */
public enum VariantStatus {

    DRAFT,

    ACTIVE,

    DISCONTINUED,

    ARCHIVED
}