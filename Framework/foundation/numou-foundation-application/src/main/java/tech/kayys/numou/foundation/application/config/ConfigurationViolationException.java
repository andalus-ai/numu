package tech.kayys.numou.foundation.application.config;

import java.util.List;

/**
 * Thrown when configuration validation detects one or more violations.
 */
public final class ConfigurationViolationException extends ConfigurationException {

    private final List<ConfigurationViolation> violations;

    public ConfigurationViolationException(String message, List<ConfigurationViolation> violations) {
        super(message);
        this.violations = violations != null ? List.copyOf(violations) : List.of();
    }

    public List<ConfigurationViolation> violations() {
        return violations;
    }
}
