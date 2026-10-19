package tech.kayys.numou.foundation.application.config.profile;

import java.util.Locale;
import java.util.Objects;

/**
 * Normalized identifier for a runtime/deployment configuration profile (config01.md §P4-03 #5).
 */
public record ConfigurationProfileId(String value) {

    public static final ConfigurationProfileId DEFAULT = new ConfigurationProfileId("default");
    public static final ConfigurationProfileId LOCAL = new ConfigurationProfileId("local");
    public static final ConfigurationProfileId DEV = new ConfigurationProfileId("dev");
    public static final ConfigurationProfileId TEST = new ConfigurationProfileId("test");
    public static final ConfigurationProfileId STAGING = new ConfigurationProfileId("staging");
    public static final ConfigurationProfileId PRODUCTION = new ConfigurationProfileId("production");

    public ConfigurationProfileId {
        Objects.requireNonNull(value, "Profile id value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Profile id cannot be blank");
        }
        value = value.trim().toLowerCase(Locale.ROOT);
    }

    public static ConfigurationProfileId of(String value) {
        return new ConfigurationProfileId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
