package tech.kayys.syirkah.commerce.configuration.domain;

import java.util.Objects;

/** Typed validation error (product02.md). */
public record ConfigurationValidationError(
        String code,
        String message
) {

    public ConfigurationValidationError {
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(message, "message cannot be null");
        code = code.trim();
        message = message.trim();
        if (code.isBlank() || message.isBlank()) {
            throw new IllegalArgumentException("code/message cannot be blank");
        }
    }

    public static ConfigurationValidationError of(String code, String message) {
        return new ConfigurationValidationError(code, message);
    }
}
