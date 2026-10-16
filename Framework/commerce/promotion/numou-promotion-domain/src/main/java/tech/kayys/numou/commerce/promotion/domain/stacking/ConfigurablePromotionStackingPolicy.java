package tech.kayys.syirkah.commerce.promotion.domain.stacking;

import tech.kayys.syirkah.commerce.promotion.domain.context.DefaultPromotionEvaluationContext;
import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionEvaluationContext;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Dispatches to STACK / EXCLUSIVE / BEST_RESULT strategies per stacking
 * group (product03.md ConfigurablePromotionStackingPolicy).
 *
 * Group rule: if any evaluation in a group is EXCLUSIVE, ExclusivePolicy
 * decides the survivor and the rest of the group is rejected.
 */
public final class ConfigurablePromotionStackingPolicy implements PromotionStackingPolicy {

    private final StackPromotionPolicy stackPolicy;
    private final ExclusivePromotionPolicy exclusivePolicy;
    private final Money originalAmount;

    public ConfigurablePromotionStackingPolicy(Money originalAmount) {
        this(originalAmount, new StackPromotionPolicy());
    }

    public ConfigurablePromotionStackingPolicy(
            Money originalAmount,
            PromotionBenefitCompatibilityPolicy compatibility
    ) {
        this(originalAmount, new StackPromotionPolicy(compatibility));
    }

    public ConfigurablePromotionStackingPolicy(
            Money originalAmount,
            StackPromotionPolicy stackPolicy
    ) {
        this.originalAmount = Objects.requireNonNull(originalAmount, "originalAmount");
        this.stackPolicy = Objects.requireNonNull(stackPolicy, "stackPolicy");
        this.exclusivePolicy = new ExclusivePromotionPolicy();
    }

    @Override
    public PromotionCompositionResult compose(List<PromotionEvaluation> evaluations) {
        return compose(evaluations, DefaultPromotionEvaluationContext.empty());
    }

    @Override
    public PromotionCompositionResult compose(
            List<PromotionEvaluation> evaluations,
            PromotionEvaluationContext context
    ) {
        Objects.requireNonNull(evaluations, "evaluations cannot be null");
        PromotionEvaluationContext ctx = context != null
                ? context
                : DefaultPromotionEvaluationContext.empty();
        if (evaluations.isEmpty()) {
            return PromotionCompositionResult.empty();
        }

        Map<String, List<PromotionEvaluation>> byGroup = new LinkedHashMap<>();
        for (var evaluation : evaluations) {
            byGroup.computeIfAbsent(
                    evaluation.stacking().stackingGroup(),
                    key -> new ArrayList<>()).add(evaluation);
        }

        List<PromotionApplied> applied = new ArrayList<>();
        List<PromotionRejected> rejected = new ArrayList<>();
        List<PromotionEvaluation> stackable = new ArrayList<>();
        List<PromotionEvaluation> bestResultPool = new ArrayList<>();

        for (var group : byGroup.values()) {
            List<PromotionEvaluation> exclusives = group.stream()
                    .filter(e -> e.stacking().mode() == PromotionStackingMode.EXCLUSIVE)
                    .toList();

            if (!exclusives.isEmpty()) {
                // Exclusive wins the whole group (including STACK siblings)
                var exclusiveResult = exclusivePolicy.compose(exclusives, ctx);
                applied.addAll(exclusiveResult.applied());
                rejected.addAll(exclusiveResult.rejected());
                var winnerId = exclusiveResult.applied().getFirst().promotionId();
                for (var evaluation : group) {
                    if (!evaluation.promotionId().equals(winnerId)
                            && exclusives.stream().noneMatch(
                                    e -> e.promotionId().equals(evaluation.promotionId()))) {
                        rejected.add(new PromotionRejected(
                                evaluation.promotionId(),
                                evaluation.promotionCode(),
                                PromotionRejectionReason.of(
                                        PromotionRejectionReason.EXCLUDED_BY_EXCLUSIVE,
                                        "Excluded by exclusive promotion in group")));
                    }
                }
                continue;
            }

            for (var evaluation : group) {
                if (evaluation.stacking().mode() == PromotionStackingMode.STACK) {
                    stackable.add(evaluation);
                } else {
                    bestResultPool.add(evaluation);
                }
            }
        }

        if (!stackable.isEmpty()) {
            var stackResult = stackPolicy.compose(stackable, ctx);
            applied.addAll(stackResult.applied());
            rejected.addAll(stackResult.rejected());
            for (var evaluation : bestResultPool) {
                rejected.add(new PromotionRejected(
                        evaluation.promotionId(),
                        evaluation.promotionCode(),
                        PromotionRejectionReason.of(
                                PromotionRejectionReason.STACK_INCOMPATIBLE,
                                "Cannot mix BEST_RESULT with STACK promotions")));
            }
            return new PromotionCompositionResult(applied, rejected);
        }

        if (!bestResultPool.isEmpty()) {
            var bestResult = new BestResultPromotionPolicy(originalAmount)
                    .compose(bestResultPool, ctx);
            applied.addAll(bestResult.applied());
            rejected.addAll(bestResult.rejected());
        }

        return new PromotionCompositionResult(applied, rejected);
    }
}
