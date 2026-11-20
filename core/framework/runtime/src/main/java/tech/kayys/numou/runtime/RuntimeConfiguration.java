package tech.kayys.numou.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Immutable, framework-neutral runtime configuration.
 *
 * <p>Configuration files use Java properties. Environment variables override
 * file values, allowing container deployments to inject configuration without
 * changing application composition code.</p>
 */
public final class RuntimeConfiguration {
    public static final String APPLICATION_ID = "numou.runtime.application-id";
    public static final String PROFILE = "numou.runtime.profile";

    private final String applicationId;
    private final String profile;
    private final Map<String, String> values;

    private RuntimeConfiguration(String applicationId, String profile, Map<String, String> values) {
        this.applicationId = requireText(applicationId, APPLICATION_ID);
        this.profile = requireText(profile, PROFILE);
        this.values = Collections.unmodifiableMap(new TreeMap<>(values));
    }

    public static RuntimeConfiguration load(Path propertiesFile) {
        Objects.requireNonNull(propertiesFile, "Configuration path is required");
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(propertiesFile)) {
            properties.load(input);
        } catch (IOException failure) {
            throw new ApplicationRuntimeException(
                    "Unable to load runtime configuration from " + propertiesFile, failure);
        }
        return from(properties, System.getenv());
    }

    public static RuntimeConfiguration from(Properties properties, Map<String, String> environment) {
        Objects.requireNonNull(properties, "Properties are required");
        Objects.requireNonNull(environment, "Environment is required");
        Map<String, String> values = new TreeMap<>();
        properties.stringPropertyNames().forEach(key -> values.put(key, properties.getProperty(key)));

        environment.forEach((key, value) -> {
            if (key.startsWith("NUMOU_RUNTIME_") && value != null) {
                String propertyKey = "numou.runtime." + key.substring("NUMOU_RUNTIME_".length())
                        .toLowerCase(java.util.Locale.ROOT).replace('_', '-');
                values.put(propertyKey, value);
            }
        });

        String applicationId = values.get(APPLICATION_ID);
        String profile = values.getOrDefault(PROFILE, "standalone");
        if (applicationId == null || applicationId.isBlank()) {
            throw new IllegalArgumentException("Missing required runtime property " + APPLICATION_ID);
        }
        return new RuntimeConfiguration(applicationId, profile, values);
    }

    public String applicationId() {
        return applicationId;
    }

    public String profile() {
        return profile;
    }

    public Map<String, String> values() {
        return values;
    }

    public java.util.Optional<String> value(String key) {
        return java.util.Optional.ofNullable(values.get(key));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
