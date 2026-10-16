package tech.kayys.syirkah.product.domain.variant;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of a product variant. */
public record ProductVariantId(UUID value) implements DomainId<UUID> {
    public ProductVariantId {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Variant id cannot be null"
            );
        }
    }

    public static ProductVariantId generate() {
        return new ProductVariantId(UUID.randomUUID());
    }

    public static ProductVariantId of(UUID value) {
        return new ProductVariantId(value);
    }
}