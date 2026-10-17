package tech.kayys.numou.commerce.promotion.domain.target;

import tech.kayys.numou.commerce.offering.domain.ProductOfferingId;

import java.util.Objects;

/** Targets a sellable product offering rather than its underlying product. */
public record ProductOfferingTarget(ProductOfferingId offeringId) implements PromotionTarget {
    public ProductOfferingTarget {
        Objects.requireNonNull(offeringId, "offeringId cannot be null");
    }
}
