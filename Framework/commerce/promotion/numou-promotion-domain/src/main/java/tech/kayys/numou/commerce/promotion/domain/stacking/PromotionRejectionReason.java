package tech.kayys.syirkah.commerce.promotion.domain.stacking;

import java.util.Objects;

/** Machine-readable reason a promotion was not applied. */
public record PromotionRejectionReason(
        String code,
        String message
) {

    public static final String EXCLUDED_BY_EXCLUSIVE =
            "excluded_by_exclusive_promotion";
    public static final String LOST_BEST_RESULT = "lost_best_result";
    public static final String STACK_INCOMPATIBLE = "stack_incompatible";
    public static final String MAX_PROMOTIONS = "max_promotions_exceeded";
    public static final String BENEFIT_CONFLICT = "benefit_conflict";

    public PromotionRejectionReason {
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(message, "message cannot be null");
        code = code.trim();
        message = message.trim();
        if (code.isBlank()) {
            throw new IllegalArgumentException("code cannot be blank");
        }
        if (message.isBlank()) {
            throw new IllegalArgumentException("message cannot be blank");
        }
    }

    public static PromotionRejectionReason of(String code, String message) {
        return new PromotionRejectionReason(code, message);
    }
}
