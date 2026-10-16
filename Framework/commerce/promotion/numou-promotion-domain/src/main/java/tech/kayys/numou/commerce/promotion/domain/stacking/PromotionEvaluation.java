package tech.kayys.syirkah.commerce.promotion.domain.stacking;

import tech.kayys.syirkah.commerce.promotion.domain.PromotionId;
import tech.kayys.syirkah.commerce.promotion.domain.benefit.PromotionBenefit;

import java.util.List;
import java.util.Objects;

/**
 * Result of evaluating one promotion by itself — before stacking.
 */
public record PromotionEvaluation(
        PromotionId promotionId,
        String promotionCode,
        int priority,
        PromotionStackingConfiguration stacking,
        List<PromotionBenefit> benefits
) {

    public PromotionEvaluation {
        Objects.requireNonNull(promotionId, "promotionId cannot be null");
        Objects.requireNonNull(promotionCode, "promotionCode cannot be null");
        Objects.requireNonNull(stacking, "stacking cannot be null");
        Objects.requireNonNull(benefits, "benefits cannot be null");
        promotionCode = promotionCode.trim();
        if (promotionCode.isBlank()) {
            throw new IllegalArgumentException("promotionCode cannot be blank");
        }
        if (benefits.isEmpty()) {
            throw new IllegalArgumentException("benefits cannot be empty");
        }
        benefits = List.copyOf(benefits);
    }
}
