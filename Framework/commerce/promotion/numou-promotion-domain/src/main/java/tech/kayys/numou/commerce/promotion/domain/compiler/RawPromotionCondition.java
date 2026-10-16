package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import tech.kayys.syirkah.commerce.promotion.domain.PromotionContext;
import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionEvaluationContext;
import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionEvaluationContexts;
import tech.kayys.syirkah.commerce.promotion.domain.condition.PromotionCondition;

/**
 * Compiled condition that delegates to a legacy raw {@link PromotionCondition}.
 *
 * <p>When a persisted promotion uses a condition type that the capability
 * registry cannot compile (for example {@code AllLinesPresent} or
 * {@code MinimumCartTotal}), compilation still succeeds by bridging the raw
 * {@code matches(PromotionContext)} predicate into the typed
 * {@link CompiledCondition} boundary. The bridge rebuilds a {@link PromotionContext}
 * from the controlled {@link PromotionEvaluationContext}, so the raw condition
 * keeps its original line-reference semantics while the compiled path stays
 * uniform.</p>
 */
public final class RawPromotionCondition implements CompiledCondition {

    private final PromotionCondition condition;

    public RawPromotionCondition(PromotionCondition condition) {
        this.condition = condition;
    }

    @Override
    public boolean evaluate(PromotionEvaluationContext context) {
        PromotionContext cart = PromotionEvaluationContexts.toPromotionContext(context);
        return condition.matches(cart);
    }
}
