package tech.kayys.numou.foundation.application.config;

import java.util.Objects;

/**
 * Represents a configuration rule violation (config01.md §P4-02 #14).
 */
public record ConfigurationViolation(
        String key,
        String code,
        String message) {

    public ConfigurationViolation {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(message, "message cannot be null");
    }
}
