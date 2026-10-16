package tech.kayys.syirkah.commerce.pricing.domain;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.util.Objects;

/**
 * One line of a price breakdown (e.g. OAT_MILK → +7,000 IDR).
 *
 * Mirrors the blueprint's {@code PriceAdjustment(amount, reason)}.
 * A negative amount is a discount/credit; the reason explains which
 * rule produced it. The pricing capability owns this — options,
 * bundles and products never carry prices themselves.
 */
public record PriceAdjustment(
        Money amount,
        String reason
) {

    public PriceAdjustment {
        Objects.requireNonNull(amount, "amount cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason cannot be blank");
        }
    }
}
