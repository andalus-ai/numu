package tech.kayys.syirkah.commerce.promotion.domain.validation;

import java.util.List;
import java.util.Objects;

/** One structural problem preventing activation. */
public record PromotionValidationError(
        String code,
        String message,
        String path
) {

    public PromotionValidationError {
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(message, "message cannot be null");
        Objects.requireNonNull(path, "path cannot be null");
    }

    public static PromotionValidationError of(String code, String message, String path) {
        return new PromotionValidationError(code, message, path);
    }

    public static PromotionValidationError of(String code, String message) {
        return new PromotionValidationError(code, message, "");
    }
}
