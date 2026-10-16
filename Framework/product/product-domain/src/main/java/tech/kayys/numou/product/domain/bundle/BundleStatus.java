package tech.kayys.syirkah.product.domain.bundle;

/**
 * Bundle lifecycle. Same vocabulary as Product/SKU:
 *
 * <pre>
 * DRAFT -&gt; ACTIVE -&gt; DISCONTINUED -&gt; ARCHIVED
 * </pre>
 *
 * Activation requires at least one component.
 */
public enum BundleStatus {

    DRAFT,

    ACTIVE,

    DISCONTINUED,

    ARCHIVED
}
