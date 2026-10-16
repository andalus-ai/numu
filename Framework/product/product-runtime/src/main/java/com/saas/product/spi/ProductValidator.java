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

import java.util.List;

/**
 * Pluggable validator for a specific extension context.
 *
 * Validators are discovered via CDI (Quarkus) or ServiceLoader.
 * Each validator declares which context it handles via {@link #supports()}.
 *
 * Validators may:
 *  - Enforce business rules (e.g. FnB products must have at least one modifier group)
 *  - Cross-validate extension data against core (e.g. variant must have parent)
 *  - Enforce regulatory constraints (e.g. pharmacy requires license number)
 */
public interface ProductValidator {

    /**
     * The extension context this validator applies to.
     * e.g. "ecommerce", "fnb", "subscription"
     */
    String supports();

    /**
     * Validate the aggregate. Throw {@link ProductValidationException} on failure.
     * Called only when the aggregate has an extension for {@link #supports()}.
     */
    void validate(ProductAggregate product);

    /**
     * Optional: collect all violations without throwing.
     * Default delegates to validate() and wraps any exception.
     */
    default List<String> collectViolations(ProductAggregate product) {
        try {
            validate(product);
            return List.of();
        } catch (ProductValidationException e) {
            return e.getViolations();
        }
    }
}
