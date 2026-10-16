package tech.kayys.numou.commerce.pricing.domain.price;

import tech.kayys.numou.commerce.offering.domain.ProductOfferingId;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.util.Objects;

/**
 * One price-list row: base price of an offering (product02.md PriceEntry).
 * Immutable value object owned by {@link PriceList}.
 */
public record PriceEntry(
        PriceEntryId id,
        ProductOfferingId offeringId,
        Money amount
) {

    public PriceEntry {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(offeringId, "offeringId cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
    }
}
