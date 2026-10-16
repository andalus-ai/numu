package tech.kayys.syirkah.commerce.promotion.domain.result;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.util.List;
import java.util.Objects;

/**
 * Original vs final amount with adjustments (product03.md).
 */
public record PromotionPriceBreakdown(
        Money originalAmount,
        List<PromotionPriceAdjustment> adjustments,
        Money finalAmount,
        Money totalDiscount
) {

    public PromotionPriceBreakdown {
        Objects.requireNonNull(originalAmount, "originalAmount cannot be null");
        Objects.requireNonNull(adjustments, "adjustments cannot be null");
        Objects.requireNonNull(finalAmount, "finalAmount cannot be null");
        Objects.requireNonNull(totalDiscount, "totalDiscount cannot be null");
        adjustments = List.copyOf(adjustments);
    }

    public static PromotionPriceBreakdown unchanged(Money original) {
        Objects.requireNonNull(original, "original cannot be null");
        return new PromotionPriceBreakdown(
                original,
                List.of(),
                original,
                Money.zero(original.currency()));
    }
}
