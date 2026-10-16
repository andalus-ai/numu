package tech.kayys.syirkah.commerce.promotion.domain;

import tech.kayys.syirkah.commerce.promotion.domain.action.BundlePrice;
import tech.kayys.syirkah.commerce.promotion.domain.action.FixedAmountOff;
import tech.kayys.syirkah.commerce.promotion.domain.action.FreeUnits;
import tech.kayys.syirkah.commerce.promotion.domain.action.PercentOff;
import tech.kayys.syirkah.commerce.promotion.domain.action.PromotionAction;
import tech.kayys.syirkah.commerce.promotion.domain.benefit.FixedDiscountBenefit;
import tech.kayys.syirkah.commerce.promotion.domain.benefit.FixedPriceBenefit;
import tech.kayys.syirkah.commerce.promotion.domain.benefit.PercentageDiscountBenefit;
import tech.kayys.syirkah.commerce.promotion.domain.benefit.PromotionBenefit;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.CompiledPromotion;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.CompiledPromotionRule;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.ConditionEvaluationResult;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.ConditionEvaluationStatus;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.PromotionEvaluationResult;
import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionEvaluationContext;
import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionEvaluationContexts;
import tech.kayys.syirkah.commerce.promotion.domain.stacking.PromotionEvaluation;
import tech.kayys.syirkah.commerce.promotion.domain.target.CartTarget;
import tech.kayys.syirkah.commerce.promotion.domain.target.LineTarget;
import tech.kayys.syirkah.commerce.promotion.domain.target.PromotionTargetSelection;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Default evaluator: multi-rule, legacy cart bridge (product03.md). */
public final class DefaultPromotionEvaluator implements PromotionEvaluator {

    @Override
    public Optional<PromotionEvaluation> evaluate(
            Promotion promotion,
            PromotionEvaluationContext context
    ) {
        Objects.requireNonNull(promotion, "promotion cannot be null");
        Objects.requireNonNull(context, "context cannot be null");

        LocalDate date = context.effectiveAt().atZone(ZoneOffset.UTC).toLocalDate();
        if (!promotion.isActiveOn(date)) {
            return Optional.empty();
        }

        PromotionContext cart = PromotionEvaluationContexts.toPromotionContext(context);
        List<PromotionBenefit> benefits = new ArrayList<>();

        for (PromotionRule rule : promotion.rulesByPriorityDesc()) {
            if (!rule.applies(cart)) {
                continue;
            }
            benefits.add(toBenefit(promotion, rule.action(), cart));
        }

        if (benefits.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new PromotionEvaluation(
                promotion.id(),
                promotion.name(),
                promotion.priority(),
                promotion.stacking(),
                benefits));
    }

    /**
     * Compiled-path equivalent of {@link #evaluate(Promotion, PromotionEvaluationContext)}.
     *
     * <p>Conditions and effects are already typed compiled objects, so no
     * capability re-resolution or reflection happens per evaluation.</p>
     */
    @Override
    public Optional<PromotionEvaluation> evaluate(
            CompiledPromotion compiled,
            PromotionEvaluationContext context
    ) {
        Objects.requireNonNull(compiled, "compiled cannot be null");
        Objects.requireNonNull(context, "context cannot be null");

        Promotion promotion = compiled.source();
        LocalDate date = context.effectiveAt().atZone(ZoneOffset.UTC).toLocalDate();
        if (!promotion.isActiveOn(date)) {
            return Optional.empty();
        }

        PromotionContext cart = PromotionEvaluationContexts.toPromotionContext(context);
        List<PromotionBenefit> benefits = new ArrayList<>();
        for (CompiledPromotionRule rule : compiled.rulesByPriorityDesc()) {
            if (!rule.condition().evaluate(context)) {
                continue;
            }
            PromotionTargetSelection targets = rule.target().resolve(context);
            benefits.addAll(rule.effect().evaluate(promotion.id(), context, targets));
        }

        if (benefits.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new PromotionEvaluation(
                promotion.id(),
                promotion.name(),
                promotion.priority(),
                promotion.stacking(),
                benefits));
    }

    /**
     * Structured evaluation of a compiled promotion (product04.md P6).
     *
     * <p>Carries every condition's reason code so callers can explain why a
     * promotion applied or did not apply.</p>
     */
    @Override
    public PromotionEvaluationResult evaluateResult(
            CompiledPromotion compiled,
            PromotionEvaluationContext context
    ) {
        Objects.requireNonNull(compiled, "compiled cannot be null");
        Objects.requireNonNull(context, "context cannot be null");

        Promotion promotion = compiled.source();
        PromotionId promotionId = promotion.id();
        // The persisted model carries no independent version yet; derive it from
        // the promotion id, matching the repository/resolver convention.
        PromotionVersionId versionId = PromotionVersionId.of(promotionId.value());

        LocalDate date = context.effectiveAt().atZone(ZoneOffset.UTC).toLocalDate();
        if (!promotion.isActiveOn(date)) {
            return PromotionEvaluationResult.skipped(
                    promotionId, versionId, "PROMOTION_INACTIVE");
        }

        List<ConditionEvaluationResult> conditions = new ArrayList<>();
        List<PromotionBenefit> benefits = new ArrayList<>();
        for (CompiledPromotionRule rule : compiled.rulesByPriorityDesc()) {
            ConditionEvaluationResult condition = rule.condition().evaluateResult(context)
                    .withCapability(rule.condition().capability());
            conditions.add(condition);

            if (condition.status() != ConditionEvaluationStatus.PASSED) {
                continue;
            }
            PromotionTargetSelection targets = rule.target().resolve(context);
            benefits.addAll(rule.effect().evaluate(promotionId, context, targets));
        }

        if (benefits.isEmpty()) {
            return PromotionEvaluationResult.notApplicable(
                    promotionId, versionId, conditions);
        }
        return PromotionEvaluationResult.applicable(
                promotionId, versionId, conditions, benefits);
    }

    private static PromotionBenefit toBenefit(
            Promotion promotion,
            PromotionAction action,
            PromotionContext cart
    ) {
        String description = promotion.name()
                + " [" + action.getClass().getSimpleName() + "]";
        return switch (action) {
            case PercentOff percentOff -> new PercentageDiscountBenefit(
                    promotion.id(), CartTarget.INSTANCE, percentOff.percentage(), description);
            case FixedAmountOff fixed -> new FixedDiscountBenefit(
                    promotion.id(),
                    CartTarget.INSTANCE,
                    fixed.amount().greaterThan(cart.total()) ? cart.total() : fixed.amount(),
                    description);
            case BundlePrice bundle -> new FixedPriceBenefit(
                    promotion.id(), CartTarget.INSTANCE, bundle.bundlePrice(), description);
            case FreeUnits free -> new FixedDiscountBenefit(
                    promotion.id(),
                    new LineTarget(free.lineRef()),
                    free.discountFor(cart),
                    description);
            default -> new FixedDiscountBenefit(
                    promotion.id(),
                    CartTarget.INSTANCE,
                    action.discountFor(cart),
                    description);
        };
    }
}
