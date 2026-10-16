package tech.kayys.syirkah.commerce.pricing.domain;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.util.List;
import java.util.Objects;

/**
 * Deterministic outcome of a price resolution.
 *
 * Mirrors the blueprint's {@code PriceResult(basePrice, adjustments,
 * finalPrice)}. The invariant {@code finalPrice = basePrice + Σ
 * adjustments} is enforced in the compact constructor so every
 * downstream consumer (promotion, subscription, order) can trust it.
 */
public record PriceResult(
        Money basePrice,
        List<PriceAdjustment> adjustments,
        Money finalPrice
) {

    public PriceResult {
        Objects.requireNonNull(basePrice, "basePrice cannot be null");
        Objects.requireNonNull(adjustments, "adjustments cannot be null");
        Objects.requireNonNull(finalPrice, "finalPrice cannot be null");
        adjustments = List.copyOf(adjustments);

        Money expected = basePrice;
        for (var adjustment : adjustments) {
            expected = expected.add(adjustment.amount());
        }
        if (expected.amount().compareTo(finalPrice.amount()) != 0
                || !expected.currency().equals(finalPrice.currency())) {
            throw new IllegalArgumentException(
                    "finalPrice must equal basePrice plus all adjustments");
        }
    }

    public static PriceResult of(Money basePrice, List<PriceAdjustment> adjustments) {
        Money total = basePrice;
        for (var adjustment : adjustments) {
            total = total.add(adjustment.amount());
        }
        return new PriceResult(basePrice, adjustments, total);
    }

    public static PriceResult flat(Money basePrice) {
        return new PriceResult(basePrice, List.of(), basePrice);
    }
}
