package tech.kayys.syirkah.commerce.promotion.domain.stacking;

import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionEvaluationContext;

import java.util.List;

/**
 * Policy layer above individual promotion evaluation (product03.md).
 * Receives already-evaluated promotions; does not re-run conditions.
 */
public interface PromotionStackingPolicy {

    PromotionCompositionResult compose(List<PromotionEvaluation> evaluations);

    default PromotionCompositionResult compose(
            List<PromotionEvaluation> evaluations,
            PromotionEvaluationContext context
    ) {
        return compose(evaluations);
    }
}
