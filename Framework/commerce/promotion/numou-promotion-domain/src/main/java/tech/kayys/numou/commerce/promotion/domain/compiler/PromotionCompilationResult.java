package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of compiling a promotion definition into a
 * {@link CompiledPromotion}.
 */
public record PromotionCompilationResult(
        Optional<CompiledPromotion> compiled,
        List<PromotionCompilationError> errors
) {

    public PromotionCompilationResult {
        Objects.requireNonNull(compiled, "compiled cannot be null");
        Objects.requireNonNull(errors, "errors cannot be null");
        errors = List.copyOf(errors);
    }

    public boolean isSuccess() {
        return compiled.isPresent() && errors.isEmpty();
    }

    public static PromotionCompilationResult success(CompiledPromotion compiled) {
        return new PromotionCompilationResult(Optional.of(compiled), List.of());
    }

    public static PromotionCompilationResult failure(
            List<PromotionCompilationError> errors) {
        return new PromotionCompilationResult(Optional.empty(), errors);
    }
}
