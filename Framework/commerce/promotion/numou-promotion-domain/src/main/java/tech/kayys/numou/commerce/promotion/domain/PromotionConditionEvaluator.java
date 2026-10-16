package tech.kayys.syirkah.commerce.promotion.domain;

import tech.kayys.syirkah.commerce.promotion.domain.compiler.ConditionEvaluationResult;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.ConditionEvaluationStatus;
import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionEvaluationContext;

/**
 * Capability-driven condition evaluator (product04.md section 5).
 *
 * <p>This is the extensibility point for new condition types. Instead of a
 * giant {@code switch} over condition types, each capability registers a
 * typed evaluator that knows how to turn a compiled condition into a
 * {@link ConditionEvaluationResult}. The compiler already resolves dynamic
 * definitions into typed compiled conditions, so the evaluator never sees
 * raw {@code Object} configuration.</p>
 */
public interface PromotionConditionEvaluator<C> {

    /** Capability identity this evaluator handles, e.g. {@code minimum_subtotal@1}. */
    String capability();

    /**
     * Evaluates the condition against the controlled context.
     *
     * @return never null; a structured result with a reason code
     */
    ConditionEvaluationResult evaluate(C condition, PromotionEvaluationContext context);

    /** Convenience: a passed result is one whose status is {@link ConditionEvaluationStatus#PASSED}. */
    static boolean passed(ConditionEvaluationResult result) {
        return result != null && result.status() == ConditionEvaluationStatus.PASSED;
    }
}
