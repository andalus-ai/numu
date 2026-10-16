package tech.kayys.syirkah.commerce.promotion.domain.context;

import java.util.Objects;

/**
 * Tenant identity on the promotion evaluation boundary.
 *
 * <p>Deliberately not the tenant aggregate identity; promotion evaluation
 * only needs the opaque tenant reference so the resolver can scope candidate
 * retrieval and the evaluator can enforce tenant isolation (product04.md §21).</p>
 */
public record TenantRef(String value) {

    public TenantRef {
        Objects.requireNonNull(value, "value cannot be null");
        value = value.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("value cannot be blank");
        }
    }

    public static TenantRef of(String value) {
        return new TenantRef(value);
    }
}
