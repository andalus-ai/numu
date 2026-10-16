package tech.kayys.numou.foundation.application.config;

import java.util.Objects;

/**
 * Metadata describing a configuration key (config01.md §P4-02 #13).
 */
public record ConfigurationMetadata(
        String key,
        boolean sensitive,
        boolean dynamic,
        boolean required) {

    public ConfigurationMetadata {
        Objects.requireNonNull(key, "key cannot be null");
    }
}
