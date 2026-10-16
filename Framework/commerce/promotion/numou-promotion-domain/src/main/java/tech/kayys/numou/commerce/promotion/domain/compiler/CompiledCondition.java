package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionEvaluationContext;

/**
 * Runtime-ready condition (product03.md).
 *
 * <p>Two evaluation entry points are kept for migration. {@link #evaluate}
 * returns a plain boolean and is used by the legacy v1 cart bridge and by
 * the {@code CompiledConditionGroup} composition. {@link #evaluateResult}
 * returns a structured {@link ConditionEvaluationResult} carrying a reason
 * code, so the P6 evaluator can explain why a promotion applied or did not
 * apply (product04.md section 10).</p>
 */
public interface CompiledCondition {

    boolean evaluate(PromotionEvaluationContext context);

    /**
     * Structured evaluation result. Defaults to a boolean projection so
     * legacy compiled conditions (e.g. {@code RawPromotionCondition}) keep
     * working without being rewritten.
     */
    default ConditionEvaluationResult evaluateResult(PromotionEvaluationContext context) {
        return evaluate(context)
                ? ConditionEvaluationResult.passed("CONDITION_MET")
                : ConditionEvaluationResult.failed("CONDITION_NOT_MET");
    }

    /** Convenience: capability identity of this compiled condition. */
    default String capability() {
        return null;
    }
}
