package tech.kayys.syirkah.commerce.promotion.domain.stacking;

import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionEvaluationContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** EXCLUSIVE mode: one survivor per stacking group by priority (product03.md). */
public final class ExclusivePromotionPolicy {

    public PromotionCompositionResult compose(
            List<PromotionEvaluation> evaluations,
            PromotionEvaluationContext context
    ) {
        Objects.requireNonNull(evaluations, "evaluations cannot be null");
        Objects.requireNonNull(context, "context cannot be null");
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

        for (var group : byGroup.values()) {
            PromotionEvaluation winner = group.stream()
                    .max(Comparator.comparingInt(PromotionEvaluation::priority)
                            .thenComparing(PromotionEvaluation::promotionCode))
                    .orElseThrow();
            applied.add(new PromotionApplied(
                    winner.promotionId(),
                    winner.promotionCode(),
                    winner.priority(),
                    winner.benefits()));
            for (var evaluation : group) {
                if (!evaluation.promotionId().equals(winner.promotionId())) {
                    rejected.add(new PromotionRejected(
                            evaluation.promotionId(),
                            evaluation.promotionCode(),
                            PromotionRejectionReason.of(
                                    PromotionRejectionReason.EXCLUDED_BY_EXCLUSIVE,
                                    "Excluded by exclusive promotion " + winner.promotionCode())));
                }
            }
        }
        return new PromotionCompositionResult(applied, rejected);
    }
}
