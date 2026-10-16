package tech.kayys.numou.foundation.application.config;

import java.time.Duration;
import java.util.Optional;

/**
 * Canonical configuration platform access contract (config01.md §P4-02 #3, #5).
 */
public interface Configuration {

    <T> Optional<T> get(ConfigurationKey<T> key);

    default <T> T require(ConfigurationKey<T> key) {
        return get(key).orElseThrow(() ->
                new ConfigurationException("Required configuration key is missing: " + key.name()));
    }

    boolean contains(ConfigurationKey<?> key);

    Optional<String> get(String key);

    default String require(String key) {
        return get(key).orElseThrow(() ->
                new ConfigurationException("Required configuration is missing: " + key));
    }

    default Optional<Boolean> getBoolean(String key) {
        return get(key).map(ConfigurationParsers::parseBoolean);
    }

    default Optional<Integer> getInt(String key) {
        return get(key).map(Integer::parseInt);
    }

    default Optional<Long> getLong(String key) {
        return get(key).map(Long::parseLong);
    }

    default Optional<Double> getDouble(String key) {
        return get(key).map(Double::parseDouble);
    }

    default Optional<Duration> getDuration(String key) {
        return get(key).map(ConfigurationParsers::parseDuration);
    }

    boolean contains(String key);

    ConfigurationSnapshot snapshot();
}
