package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import java.util.Objects;

/**
 * A human-readable reason attached to a promotion evaluation result
 * (product04.md section 3).
 */
public record PromotionEvaluationReason(
        PromotionEvaluationStatus status,
        String code,
        String message
) {

    public PromotionEvaluationReason {
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(message, "message cannot be null");
    }

    public static PromotionEvaluationReason of(
            PromotionEvaluationStatus status,
            String code,
            String message
    ) {
        return new PromotionEvaluationReason(status, code, message);
    }
}
