package tech.kayys.syirkah.product.domain.product;

/**
 * Product lifecycle. The order is a domain rule, not a database
 * field: ARCHIVED and DISCONTINUED can never move backwards.
 *
 * <pre>
 * DRAFT -&gt; ACTIVE -&gt; DISCONTINUED -&gt; ARCHIVED
 * </pre>
 *
 * Products with historical transactions are logically deactivated
 * (DISCONTINUED/ARCHIVED), never physically deleted.
 */
public enum ProductStatus {

    DRAFT,

    ACTIVE,

    DISCONTINUED,

    ARCHIVED
}