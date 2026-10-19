package tech.kayys.numou.foundation.application.config;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable snapshot of configuration at a point in time (config01.md §P4-02 #11).
 */
public interface ConfigurationSnapshot {

    <T> Optional<T> get(ConfigurationKey<T> key);

    default <T> T require(ConfigurationKey<T> key) {
        return get(key).orElseThrow(() ->
                new ConfigurationException("Required configuration key is missing: " + key.name()));
    }

    Optional<String> get(String key);

    default String require(String key) {
        return get(key).orElseThrow(() ->
                new ConfigurationException("Required configuration is missing: " + key));
    }

    boolean contains(ConfigurationKey<?> key);

    boolean contains(String key);

    Instant createdAt();

    Map<String, String> rawEntries();

    Map<String, String> toRedactedMap();
}
