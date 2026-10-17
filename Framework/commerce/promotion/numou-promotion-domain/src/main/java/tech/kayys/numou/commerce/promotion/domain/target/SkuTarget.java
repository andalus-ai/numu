package tech.kayys.numou.commerce.promotion.domain.target;

import tech.kayys.numou.product.domain.sku.SkuId;

import java.util.Objects;

/** Targets a specific SKU identity. */
public record SkuTarget(SkuId skuId) implements PromotionTarget {
    public SkuTarget {
        Objects.requireNonNull(skuId, "skuId cannot be null");
    }
}
