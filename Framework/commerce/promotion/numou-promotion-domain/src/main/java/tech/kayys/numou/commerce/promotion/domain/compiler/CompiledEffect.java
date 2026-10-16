package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import tech.kayys.syirkah.commerce.promotion.domain.PromotionId;
import tech.kayys.syirkah.commerce.promotion.domain.benefit.PromotionBenefit;
import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionEvaluationContext;
import tech.kayys.syirkah.commerce.promotion.domain.target.PromotionTargetSelection;

import java.util.List;

/**
 * Runtime-ready effect (product03.md). {@code promotionId} is retained so
 * benefits that require attribution (e.g. {@code PercentageDiscountBenefit})
 * can be constructed without embedding the id in every effect factory.
 */
public interface CompiledEffect {

    List<PromotionBenefit> evaluate(
            PromotionId promotionId,
            PromotionEvaluationContext context,
            PromotionTargetSelection targets
    );
}
