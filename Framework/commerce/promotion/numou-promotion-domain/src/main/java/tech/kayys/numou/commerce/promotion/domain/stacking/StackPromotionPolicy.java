package tech.kayys.syirkah.commerce.promotion.domain.stacking;

import tech.kayys.syirkah.commerce.promotion.domain.benefit.PromotionBenefit;
import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionEvaluationContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * STACK mode: retain all applicable benefits unless compatibility
 * policy rejects a combination (product03.md).
 */
public final class StackPromotionPolicy {

    private final PromotionBenefitCompatibilityPolicy compatibilityPolicy;

    public StackPromotionPolicy() {
        this(new DefaultPromotionBenefitCompatibilityPolicy());
    }

    public StackPromotionPolicy(PromotionBenefitCompatibilityPolicy compatibilityPolicy) {
        this.compatibilityPolicy = Objects.requireNonNull(
                compatibilityPolicy, "compatibilityPolicy cannot be null");
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

        List<PromotionEvaluation> ordered = evaluations.stream()
                .sorted(Comparator
                        .comparingInt(PromotionEvaluation::priority)
                        .thenComparing(PromotionEvaluation::promotionCode))
                .toList();

        List<PromotionApplied> applied = new ArrayList<>();
        List<PromotionRejected> rejected = new ArrayList<>();

        for (PromotionEvaluation candidate : ordered) {
            List<PromotionBenefit> acceptedBenefits =
                    acceptCompatibleBenefits(candidate, applied, context);

            if (acceptedBenefits.isEmpty()) {
                rejected.add(new PromotionRejected(
                        candidate.promotionId(),
                        candidate.promotionCode(),
                        PromotionRejectionReason.of(
                                PromotionRejectionReason.BENEFIT_CONFLICT,
                                "Promotion benefits conflict with already accepted benefits")));
                continue;
            }

            applied.add(new PromotionApplied(
                    candidate.promotionId(),
                    candidate.promotionCode(),
                    candidate.priority(),
                    acceptedBenefits));
        }

        return new PromotionCompositionResult(applied, rejected);
    }

    private List<PromotionBenefit> acceptCompatibleBenefits(
            PromotionEvaluation candidate,
            List<PromotionApplied> applied,
            PromotionEvaluationContext context
    ) {
        List<PromotionBenefit> accepted = new ArrayList<>();

        for (PromotionBenefit benefit : candidate.benefits()) {
            boolean compatible = Stream.concat(
                            applied.stream().flatMap(a -> a.benefits().stream()),
                            accepted.stream())
                    .allMatch(existing ->
                            compatibilityPolicy.canCombine(existing, benefit, context));

            if (compatible) {
                accepted.add(benefit);
            }
        }

        return accepted;
    }
}
