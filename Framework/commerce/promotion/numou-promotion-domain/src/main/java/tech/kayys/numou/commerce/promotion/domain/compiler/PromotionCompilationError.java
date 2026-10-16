package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import java.util.List;
import java.util.Objects;

/** Structured compile diagnostics (product03.md). */
public record PromotionCompilationError(
        String code,
        String message,
        String path
) {

    public PromotionCompilationError {
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(message, "message cannot be null");
        Objects.requireNonNull(path, "path cannot be null");
    }

    public static PromotionCompilationError of(
            String code, String message, String path) {
        return new PromotionCompilationError(code, message, path);
    }
}
