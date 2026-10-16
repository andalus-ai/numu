package tech.kayys.syirkah.product.domain.specification;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of a product specification. */
public record ProductSpecificationId(UUID value)
        implements DomainId<UUID> {

    public ProductSpecificationId {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Product specification id cannot be null"
            );
        }
    }

    public static ProductSpecificationId generate() {
        return new ProductSpecificationId(UUID.randomUUID());
    }

    public static ProductSpecificationId of(UUID value) {
        return new ProductSpecificationId(value);
    }
}