package tech.kayys.syirkah.commerce.promotion.domain.observability;

import tech.kayys.syirkah.commerce.promotion.domain.PromotionId;
import tech.kayys.syirkah.commerce.promotion.domain.stacking.PromotionApplied;
import tech.kayys.syirkah.commerce.promotion.domain.stacking.PromotionRejected;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds structured evaluation traces from applied/rejected promotions.
 * Accepted benefits are listed per promotion; rejected ones carry the
 * policy reason so support can explain checkout outcomes.
 */
public final class PromotionEvaluationTracer {

    private PromotionEvaluationTracer() {
    }

    public static List<PromotionEvaluationTrace> trace(
            List<PromotionId> candidates,
            List<PromotionApplied> applied,
            List<PromotionRejected> rejected
    ) {
        Objects.requireNonNull(candidates, "candidates cannot be null");
        Objects.requireNonNull(applied, "applied cannot be null");
        Objects.requireNonNull(rejected, "rejected cannot be null");

        var appliedById = new java.util.HashMap<PromotionId, PromotionApplied>();
        for (var entry : applied) {
            appliedById.put(entry.promotionId(), entry);
        }
        var rejectedById = new java.util.HashMap<PromotionId, PromotionRejected>();
        for (var entry : rejected) {
            rejectedById.put(entry.promotionId(), entry);
        }

        List<PromotionEvaluationTrace> traces = new ArrayList<>();
        for (var candidateId : candidates) {
            var accepted = appliedById.get(candidateId);
            var denied = rejectedById.get(candidateId);
            if (accepted != null) {
                traces.add(new PromotionEvaluationTrace(
                        accepted.promotionId(),
                        accepted.promotionCode(),
                        true,
                        "matched",
                        String.valueOf(accepted.benefits().size()),
                        accepted.benefits().toString(),
                        "accepted",
                        ""));
            } else if (denied != null) {
                traces.add(new PromotionEvaluationTrace(
                        denied.promotionId(),
                        denied.promotionCode(),
                        true,
                        "matched",
                        "[]",
                        "[]",
                        "rejected",
                        denied.reason().code()));
            } else {
                traces.add(new PromotionEvaluationTrace(
                        candidateId,
                        candidateId.value().toString(),
                        true,
                        "not-matched",
                        "[]",
                        "[]",
                        "skipped",
                        "condition-not-matched"));
            }
        }
        return List.copyOf(traces);
    }
}
