package com.saas.product.spi;

import java.util.List;

/**
 * Thrown when product validation fails.
 * Carries a list of human-readable violation messages.
 */
public class ProductValidationException extends RuntimeException {

    private final List<String> violations;

    public ProductValidationException(List<String> violations) {
        super("Product validation failed: " + violations);
        this.violations = List.copyOf(violations);
    }

    public ProductValidationException(String violation) {
        this(List.of(violation));
    }

    public List<String> getViolations() {
        return violations;
    }
}
