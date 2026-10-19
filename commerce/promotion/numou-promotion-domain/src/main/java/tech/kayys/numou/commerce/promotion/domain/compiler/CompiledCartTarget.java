package tech.kayys.numou.commerce.promotion.domain.compiler;

import tech.kayys.numou.commerce.promotion.domain.context.PromotionEvaluationContext;
import tech.kayys.numou.commerce.promotion.domain.target.CartTarget;
import tech.kayys.numou.commerce.promotion.domain.target.PromotionTargetSelection;
import tech.kayys.numou.commerce.promotion.domain.target.SingleTargetSelection;

/** Resolves the whole cart as the effect target. */
public record CompiledCartTarget() implements CompiledTarget {

    @Override
    public PromotionTargetSelection resolve(PromotionEvaluationContext context) {
        return new SingleTargetSelection(CartTarget.INSTANCE);
    }
}
