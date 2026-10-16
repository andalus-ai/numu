package tech.kayys.syirkah.commerce.promotion.domain.context;

import java.util.UUID;

/**
 * SKU identity on the promotion evaluation boundary.
 *
 * <p>Deliberately not the product aggregate's SKU identity; promotion
 * evaluation only needs the opaque SKU reference so the resolver can narrow
 * candidates by SKU index (product04.md §10).</p>
 */
public record SkuId(UUID value) {

    public SkuId {
        if (value == null) {
            throw new IllegalArgumentException("SKU id cannot be null");
        }
    }

    public static SkuId generate() {
        return new SkuId(UUID.randomUUID());
    }

    public static SkuId of(UUID value) {
        return new SkuId(value);
    }
}
