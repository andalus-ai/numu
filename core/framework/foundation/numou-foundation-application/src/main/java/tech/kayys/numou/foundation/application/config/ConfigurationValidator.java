package tech.kayys.numou.foundation.application.config;

import java.util.List;

/**
 * Validates a configuration snapshot prior to runtime start (config01.md §P4-02 #14).
 */
public interface ConfigurationValidator {

    /**
     * Validates the configuration snapshot and returns any violations detected.
     */
    List<ConfigurationViolation> validate(ConfigurationSnapshot snapshot);
}
