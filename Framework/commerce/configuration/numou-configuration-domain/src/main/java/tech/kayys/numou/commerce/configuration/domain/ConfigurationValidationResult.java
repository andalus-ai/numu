package tech.kayys.syirkah.commerce.configuration.domain;

import java.util.List;
import java.util.Objects;

/**
 * Outcome of validating a configuration against a specification.
 */
public record ConfigurationValidationResult(
        List<ConfigurationValidationError> errors
) {

    public ConfigurationValidationResult {
        Objects.requireNonNull(errors, "errors cannot be null");
        errors = List.copyOf(errors);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    /** Legacy accessor used by existing tests. */
    public List<String> violations() {
        return errors.stream().map(ConfigurationValidationError::message).toList();
    }

    public static ConfigurationValidationResult valid() {
        return new ConfigurationValidationResult(List.of());
    }

    public static ConfigurationValidationResult invalid(
            List<ConfigurationValidationError> errors
    ) {
        if (errors == null || errors.isEmpty()) {
            throw new IllegalArgumentException(
                    "An invalid result must carry at least one error");
        }
        return new ConfigurationValidationResult(errors);
    }

    public static ConfigurationValidationResult invalid(String... messages) {
        return invalid(java.util.Arrays.stream(messages)
                .map(m -> ConfigurationValidationError.of("VALIDATION", m))
                .toList());
    }

    public static ConfigurationValidationResult invalidMessages(List<String> messages) {
        return invalid(messages.stream()
                .map(m -> ConfigurationValidationError.of("VALIDATION", m))
                .toList());
    }
}
