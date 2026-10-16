package tech.kayys.syirkah.commerce.promotion.domain.compiler;

/**
 * Compilation error (product03.md). Carries a stable code, a human message
 * and the JSON path where the problem lives so callers can surface it to
 * tenants or log it deterministically.
 */
public final class PromotionCompilationException extends RuntimeException {

    public PromotionCompilationException(String message) {
        super(message);
    }

    public PromotionCompilationException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Stable, machine-readable error code. */
    public static PromotionCompilationException of(
            String code, String message, String path) {
        return new PromotionCompilationException(
                code + ": " + message + " (at " + path + ")");
    }
}
