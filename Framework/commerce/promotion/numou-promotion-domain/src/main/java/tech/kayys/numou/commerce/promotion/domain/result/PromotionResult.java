package tech.kayys.syirkah.commerce.promotion.domain.result;

import tech.kayys.syirkah.commerce.promotion.domain.stacking.PromotionApplied;
import tech.kayys.syirkah.commerce.promotion.domain.stacking.PromotionRejected;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.util.List;
import java.util.Objects;

/**
 * Full promotion execution result (product03.md).
 */
public record PromotionResult(
        Money originalAmount,
        List<PromotionApplied> applied,
        List<PromotionRejected> rejected,
        Money finalAmount,
        PromotionPriceBreakdown price
) {

    public PromotionResult {
        Objects.requireNonNull(originalAmount, "originalAmount cannot be null");
        Objects.requireNonNull(applied, "applied cannot be null");
        Objects.requireNonNull(rejected, "rejected cannot be null");
        Objects.requireNonNull(finalAmount, "finalAmount cannot be null");
        Objects.requireNonNull(price, "price cannot be null");
        applied = List.copyOf(applied);
        rejected = List.copyOf(rejected);
    }

    public boolean hasApplications() {
        return !applied.isEmpty();
    }

    public static PromotionResult none(Money original) {
        return new PromotionResult(
                original,
                List.of(),
                List.of(),
                original,
                PromotionPriceBreakdown.unchanged(original));
    }

    public static PromotionResult of(
            Money original,
            List<PromotionApplied> applied,
            List<PromotionRejected> rejected,
            PromotionPriceBreakdown price
    ) {
        return new PromotionResult(
                original,
                applied,
                rejected,
                price.finalAmount(),
                price);
    }
}
