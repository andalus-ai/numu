package tech.kayys.syirkah.commerce.promotion.domain.stacking;

import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionEvaluationContext;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** BEST_RESULT mode: single global monetary winner (product03.md). */
public final class BestResultPromotionPolicy {

    private final Money originalAmount;

    public BestResultPromotionPolicy(Money originalAmount) {
        this.originalAmount = Objects.requireNonNull(originalAmount, "originalAmount");
    }

    public PromotionCompositionResult compose(
            List<PromotionEvaluation> evaluations,
            PromotionEvaluationContext context
    ) {
        Objects.requireNonNull(evaluations, "evaluations cannot be null");
        Objects.requireNonNull(context, "context cannot be null");
        if (evaluations.isEmpty()) {
            return PromotionCompositionResult.empty();
        }

        PromotionEvaluation winner = evaluations.stream()
                .max((a, b) -> MonetaryPromotionComparisonPolicy.compare(a, b, originalAmount))
                .orElseThrow();

        List<PromotionApplied> applied = List.of(new PromotionApplied(
                winner.promotionId(),
                winner.promotionCode(),
                winner.priority(),
                winner.benefits()));
        List<PromotionRejected> rejected = new ArrayList<>();
        for (var evaluation : evaluations) {
            if (!evaluation.promotionId().equals(winner.promotionId())) {
                rejected.add(new PromotionRejected(
                        evaluation.promotionId(),
                        evaluation.promotionCode(),
                        PromotionRejectionReason.of(
                                PromotionRejectionReason.LOST_BEST_RESULT,
                                "Lost to better promotion " + winner.promotionCode())));
            }
        }
        return new PromotionCompositionResult(applied, rejected);
    }
}
