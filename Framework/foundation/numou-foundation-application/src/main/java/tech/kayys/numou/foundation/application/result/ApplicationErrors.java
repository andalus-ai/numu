package tech.kayys.numou.foundation.application.result;

import java.util.Objects;

/**
 * Factory utilities for constructing canonical {@link ApplicationError} instances.
 */
public final class ApplicationErrors {

    private ApplicationErrors() {}

    public static ApplicationError notFound(String resource, Object identifier) {
        return ApplicationError.of("NOT_FOUND", resource + " not found with identifier: " + identifier);
    }

    public static ApplicationError notFound(String message) {
        return ApplicationError.of("NOT_FOUND", message);
    }

    public static ApplicationError validation(String message) {
        return ApplicationError.of("VALIDATION_FAILED", message);
    }

    public static ApplicationError conflict(String message) {
        return ApplicationError.of("CONFLICT", message);
    }

    public static ApplicationError unauthorized(String message) {
        return ApplicationError.of("UNAUTHORIZED", message);
    }

    public static ApplicationError forbidden(String message) {
        return ApplicationError.of("FORBIDDEN", message);
    }

    public static ApplicationError businessRuleViolation(String message) {
        return ApplicationError.of("BUSINESS_RULE_VIOLATION", message);
    }

    public static ApplicationError internal(String message) {
        return ApplicationError.of("INTERNAL_ERROR", message);
    }

    public static ApplicationError internal(Throwable throwable) {
        Objects.requireNonNull(throwable, "throwable cannot be null");
        String msg = throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName();
        return ApplicationError.of("INTERNAL_ERROR", msg);
    }
}
