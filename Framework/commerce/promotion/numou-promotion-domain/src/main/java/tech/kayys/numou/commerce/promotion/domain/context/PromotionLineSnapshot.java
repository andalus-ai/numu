package tech.kayys.syirkah.commerce.promotion.domain.context;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.foundation.domain.valueobject.Quantity;
import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.product.domain.sku.SkuId;

import java.util.Objects;
import java.util.Optional;

/**
 * Controlled line read model for promotion evaluation.
 * {@code offeringRef} stands in for ProductOfferingId when that type
 * is not on the promotion classpath.
 */
public record PromotionLineSnapshot(
        PromotionLineId id,
        Optional<ProductId> productId,
        Optional<SkuId> skuId,
        Optional<CategoryId> categoryId,
        String offeringRef,
        Quantity quantity,
        Money unitPrice
) {

    public PromotionLineSnapshot {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(productId, "productId cannot be null");
        Objects.requireNonNull(skuId, "skuId cannot be null");
        Objects.requireNonNull(categoryId, "categoryId cannot be null");
        Objects.requireNonNull(offeringRef, "offeringRef cannot be null");
        Objects.requireNonNull(quantity, "quantity cannot be null");
        Objects.requireNonNull(unitPrice, "unitPrice cannot be null");
        offeringRef = offeringRef.trim();
        if (offeringRef.isBlank()) {
            throw new IllegalArgumentException("offeringRef cannot be blank");
        }
    }

    /** Convenience constructor that omits the category reference. */
    public PromotionLineSnapshot(
            PromotionLineId id,
            Optional<ProductId> productId,
            Optional<SkuId> skuId,
            String offeringRef,
            Quantity quantity,
            Money unitPrice
    ) {
        this(id, productId, skuId, Optional.empty(), offeringRef, quantity, unitPrice);
    }
}
