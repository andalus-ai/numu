package tech.kayys.syirkah.commerce.promotion.domain.observability;

import tech.kayys.syirkah.commerce.promotion.domain.PromotionId;

import java.util.Objects;

/**
 * One structured evaluation trace line (product03.md section 28).
 * Internal diagnostics — not exposed to customers — retained for
 * checkout recalculation, audit and debugging.
 */
public record PromotionEvaluationTrace(
        PromotionId promotionId,
        String promotionCode,
        boolean candidate,
        String condition,
        String targets,
        String benefits,
        String stacking,
        String stackingReason
) {

    public PromotionEvaluationTrace {
        Objects.requireNonNull(promotionId, "promotionId cannot be null");
        Objects.requireNonNull(promotionCode, "promotionCode cannot be null");
        Objects.requireNonNull(condition, "condition cannot be null");
        Objects.requireNonNull(targets, "targets cannot be null");
        Objects.requireNonNull(benefits, "benefits cannot be null");
        Objects.requireNonNull(stacking, "stacking cannot be null");
        Objects.requireNonNull(stackingReason, "stackingReason cannot be null");
    }
}
