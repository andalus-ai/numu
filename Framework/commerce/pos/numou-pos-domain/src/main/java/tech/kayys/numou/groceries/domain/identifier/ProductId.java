package tech.kayys.syirkah.groceries.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/**
 * POS (Groceries) bounded-context product identity.
 *
 * <p>This is <b>not</b> the Product aggregate identity. It is a
 * context-local reference to a product that lives in the Catalog /
 * Product bounded context. Keeping it in its own package prevents
 * accidental cross-context mixing with
 * {@code tech.kayys.syirkah.product.domain.product.ProductId}.
 *
 * <p>Consider renaming to {@code PosProductRef} if the team prefers
 * an explicit "reference" naming convention across all contexts.</p>
 *
 * @deprecated kept for source compatibility; new code should use
 *     {@code PosProductRef} once the rename is complete.
 */
@Deprecated
public record ProductId(UUID value) implements DomainId<UUID>, Serializable {

    public ProductId {
        Objects.requireNonNull(value, "ProductId value cannot be null");
    }

    public static ProductId of(UUID value) {
        return new ProductId(value);
    }

    public static ProductId generate() {
        return new ProductId(UUID.randomUUID());
    }

    public static ProductId fromString(String value) {
        return new ProductId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "ProductId{" + value + "}";
    }
}
