package tech.kayys.syirkah.commerce.promotion.domain;

import tech.kayys.syirkah.commerce.promotion.domain.compiler.CompiledPromotion;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.PromotionEvaluationResult;
import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionEvaluationContext;
import tech.kayys.syirkah.commerce.promotion.domain.stacking.PromotionEvaluation;

import java.util.Optional;

/**
 * Qualifies a promotion and produces candidate benefits (product03.md).
 *
 * <p>Two evaluation paths are kept for migration:</p>
 * <ul>
 *   <li>{@link #evaluate(Promotion, PromotionEvaluationContext)} -- legacy
 *       path over the persisted aggregate, returning a bare
 *       {@link Optional}&lt;{@link PromotionEvaluation}&gt;. This keeps the
 *       v1 cart bridge working.</li>
 *   <li>{@link #evaluate(CompiledPromotion, PromotionEvaluationContext)} --
 *       typed path over the compiler output, returning a structured
 *       {@link PromotionEvaluationResult} with status, reason codes and
 *       per-condition detail (product04.md P6). This is the recommended path
 *       for new callers.</li>
 * </ul>
 */
public interface PromotionEvaluator {

    /**
     * Evaluates a persisted promotion against a controlled evaluation context.
     */
    Optional<PromotionEvaluation> evaluate(
            Promotion promotion,
            PromotionEvaluationContext context
    );

    /**
     * Evaluates a compiled promotion against a controlled evaluation context.
     *
     * <p>Compiled promotions are produced by the compiler from the capability
     * registry, so their conditions/effects are already typed runtime objects.
     * This overload lets the evaluator work directly from the cached compiled
     * form without re-resolving capabilities per evaluation.</p>
     */
    Optional<PromotionEvaluation> evaluate(
            CompiledPromotion compiled,
            PromotionEvaluationContext context
    );

    /**
     * Structured evaluation of a compiled promotion (product04.md P6).
     *
     * <p>This is the primary P6 entry point. It returns a
     * {@link PromotionEvaluationResult} carrying the four-way
     * {@link tech.kayys.syirkah.commerce.promotion.domain.compiler.PromotionEvaluationStatus}
     * and a reason code for every condition, so callers can explain why a
     * promotion applied or did not apply.</p>
     */
    PromotionEvaluationResult evaluateResult(
            CompiledPromotion compiled,
            PromotionEvaluationContext context
    );
}
