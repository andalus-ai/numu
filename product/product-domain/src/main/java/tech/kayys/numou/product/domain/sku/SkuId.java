package tech.kayys.numou.product.domain.sku;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of a stock keeping unit. */
public record SkuId(UUID value) implements DomainId<UUID> {
    public SkuId {
        Objects.requireNonNull(value, "SKU id cannot be null");
    }

    public static SkuId generate() {
        return new SkuId(UUID.randomUUID());
    }

    public static SkuId of(UUID value) {
        return new SkuId(value);
    }
}