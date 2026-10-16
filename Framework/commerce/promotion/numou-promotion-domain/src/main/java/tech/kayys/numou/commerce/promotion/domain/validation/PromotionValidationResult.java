package tech.kayys.syirkah.commerce.promotion.domain.validation;

import java.util.List;
import java.util.Objects;

/**
 * Activation-time validation result (product03.md PromotionValidator).
 * Runtime evaluation may assume a valid promotion.
 */
public record PromotionValidationResult(
        boolean valid,
        List<PromotionValidationError> errors
) {

    public PromotionValidationResult {
        Objects.requireNonNull(errors, "errors cannot be null");
        errors = List.copyOf(errors);
        if (valid && !errors.isEmpty()) {
            throw new IllegalArgumentException("valid result cannot carry errors");
        }
        if (!valid && errors.isEmpty()) {
            throw new IllegalArgumentException("invalid result must carry errors");
        }
    }

    public static PromotionValidationResult ok() {
        return new PromotionValidationResult(true, List.of());
    }

    public static PromotionValidationResult invalid(List<PromotionValidationError> errors) {
        return new PromotionValidationResult(false, errors);
    }
}
