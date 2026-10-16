package tech.kayys.syirkah.commerce.promotion.domain.stacking;

import java.util.List;
import java.util.Objects;

/** Outcome of composing independently evaluated promotions. */
public record PromotionCompositionResult(
        List<PromotionApplied> applied,
        List<PromotionRejected> rejected
) {

    public PromotionCompositionResult {
        Objects.requireNonNull(applied, "applied cannot be null");
        Objects.requireNonNull(rejected, "rejected cannot be null");
        applied = List.copyOf(applied);
        rejected = List.copyOf(rejected);
    }

    public static PromotionCompositionResult empty() {
        return new PromotionCompositionResult(List.of(), List.of());
    }
}
