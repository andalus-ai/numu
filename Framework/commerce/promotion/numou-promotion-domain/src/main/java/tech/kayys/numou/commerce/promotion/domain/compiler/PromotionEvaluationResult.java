package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import tech.kayys.syirkah.commerce.promotion.domain.PromotionId;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionVersionId;
import tech.kayys.syirkah.commerce.promotion.domain.benefit.PromotionBenefit;

import java.util.List;
import java.util.Objects;

/**
 * Result of evaluating one promotion against a controlled context
 * (product04.md section 3).
 *
 * <p>The four-way {@link PromotionEvaluationStatus} split matters for
 * observability, performance, debugging and simulation:</p>
 * <ul>
 *   <li>{@link PromotionEvaluationStatus#APPLICABLE} -- the promotion was
 *       evaluated and its conditions passed.</li>
 *   <li>{@link PromotionEvaluationStatus#NOT_APPLICABLE} -- the promotion was
 *       evaluated and at least one condition failed.</li>
 *   <li>{@link PromotionEvaluationStatus#SKIPPED} -- the system deliberately
 *       did not evaluate the promotion because a gate already established it
 *       was not relevant (e.g. channel mismatch discovered in P5).</li>
 *   <li>{@link PromotionEvaluationStatus#INVALID} -- the promotion is in a
 *       state that cannot be evaluated.</li>
 * </ul>
 */
public record PromotionEvaluationResult(
        PromotionId promotionId,
        PromotionVersionId versionId,
        PromotionEvaluationStatus status,
        List<ConditionEvaluationResult> conditions,
        List<PromotionBenefit> benefits,
        List<PromotionEvaluationReason> reasons
) {

    public PromotionEvaluationResult {
        Objects.requireNonNull(promotionId, "promotionId cannot be null");
        Objects.requireNonNull(versionId, "versionId cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(conditions, "conditions cannot be null");
        Objects.requireNonNull(benefits, "benefits cannot be null");
        Objects.requireNonNull(reasons, "reasons cannot be null");
        conditions = List.copyOf(conditions);
        benefits = List.copyOf(benefits);
        reasons = List.copyOf(reasons);
    }

    public boolean isApplicable() {
        return status == PromotionEvaluationStatus.APPLICABLE;
    }

    public static PromotionEvaluationResult applicable(
            PromotionId promotionId,
            PromotionVersionId versionId,
            List<ConditionEvaluationResult> conditions,
            List<PromotionBenefit> benefits
    ) {
        return new PromotionEvaluationResult(
                promotionId,
                versionId,
                PromotionEvaluationStatus.APPLICABLE,
                conditions,
                benefits,
                List.of(PromotionEvaluationReason.of(
                        PromotionEvaluationStatus.APPLICABLE,
                        "PROMOTION_APPLIED",
                        "Promotion " + promotionId.value() + " applied")));
    }

    public static PromotionEvaluationResult notApplicable(
            PromotionId promotionId,
            PromotionVersionId versionId,
            List<ConditionEvaluationResult> conditions
    ) {
        return new PromotionEvaluationResult(
                promotionId,
                versionId,
                PromotionEvaluationStatus.NOT_APPLICABLE,
                conditions,
                List.of(),
                List.of(PromotionEvaluationReason.of(
                        PromotionEvaluationStatus.NOT_APPLICABLE,
                        "PROMOTION_NOT_APPLICABLE",
                        "Promotion " + promotionId.value() + " did not satisfy its conditions")));
    }

    public static PromotionEvaluationResult skipped(
            PromotionId promotionId,
            PromotionVersionId versionId,
            String reasonCode
    ) {
        return new PromotionEvaluationResult(
                promotionId,
                versionId,
                PromotionEvaluationStatus.SKIPPED,
                List.of(),
                List.of(),
                List.of(PromotionEvaluationReason.of(
                        PromotionEvaluationStatus.SKIPPED,
                        reasonCode,
                        "Promotion " + promotionId.value() + " skipped: " + reasonCode)));
    }

    public static PromotionEvaluationResult invalid(
            PromotionId promotionId,
            PromotionVersionId versionId,
            String reasonCode
    ) {
        return new PromotionEvaluationResult(
                promotionId,
                versionId,
                PromotionEvaluationStatus.INVALID,
                List.of(),
                List.of(),
                List.of(PromotionEvaluationReason.of(
                        PromotionEvaluationStatus.INVALID,
                        reasonCode,
                        "Promotion " + promotionId.value() + " is invalid: " + reasonCode)));
    }
}
