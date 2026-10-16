package tech.kayys.numou.commerce.promotion.domain.compiler;

import tech.kayys.numou.commerce.promotion.domain.context.PromotionEvaluationContext;
import tech.kayys.numou.commerce.promotion.domain.target.PromotionTargetSelection;

/** Runtime-ready target resolver (product03.md). */
@FunctionalInterface
public interface CompiledTarget {

    PromotionTargetSelection resolve(PromotionEvaluationContext context);
}
