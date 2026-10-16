package tech.kayys.syirkah.product.domain.product;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of a product. */
public record ProductId(UUID value) implements DomainId<UUID> {
    public ProductId {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Product id cannot be null"
            );
        }
    }

    public static ProductId generate() {
        return new ProductId(UUID.randomUUID());
    }

    public static ProductId of(UUID value) {
        return new ProductId(value);
    }

    public static ProductId fromString(String value) {
        return new ProductId(UUID.fromString(value));
    }
}