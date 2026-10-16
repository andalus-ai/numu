package tech.kayys.syirkah.product.domain.product;

/**
 * The nature of a business offering. Product Foundation must never
 * assume everything sold is a physical good.
 *
 * <pre>
 * Indomie            -&gt; PHYSICAL
 * Consulting         -&gt; SERVICE
 * Netflix Plan       -&gt; SUBSCRIPTION
 * API Call           -&gt; DIGITAL
 * Shipping Fee       -&gt; FEE
 * Gift Basket        -&gt; BUNDLE
 * </pre>
 */
public enum ProductType {

    PHYSICAL,

    DIGITAL,

    SERVICE,

    SUBSCRIPTION,

    FEE,

    BUNDLE,

    CONFIGURABLE
}