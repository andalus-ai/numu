package tech.kayys.numou.foundation.application.config;

import java.time.Duration;
import java.util.*;

/**
 * Standard runtime configuration keys (config01.md §P4-02).
 */
public final class RuntimeConfigurationKeys {

    public static final ConfigurationKey<String> APPLICATION_NAME =
            ConfigurationKey.builder("numou.application.name", String.class)
                    .defaultValue("numou-platform")
                    .required()
                    .description("The canonical name of the application runtime")
                    .build();

    public static final ConfigurationKey<String> APPLICATION_VERSION =
            ConfigurationKey.builder("numou.application.version", String.class)
                    .defaultValue("1.0.0-SNAPSHOT")
                    .required()
                    .description("The version of the application runtime")
                    .build();

    public static final ConfigurationKey<String> ACTIVE_PROFILE =
            ConfigurationKey.builder("numou.runtime.profile", String.class)
                    .defaultValue("default")
                    .description("The active configuration profile")
                    .build();

    public static final ConfigurationKey<Duration> STARTUP_TIMEOUT =
            ConfigurationKey.builder("numou.runtime.startup-timeout", Duration.class)
                    .defaultValue(Duration.ofSeconds(60))
                    .description("Maximum duration allowed for runtime startup")
                    .build();

    public static final ConfigurationKey<Duration> SHUTDOWN_TIMEOUT =
            ConfigurationKey.builder("numou.runtime.shutdown-timeout", Duration.class)
                    .defaultValue(Duration.ofSeconds(30))
                    .description("Maximum duration allowed for graceful runtime shutdown")
                    .build();

    public static final Set<ConfigurationKey<?>> ALL = Set.of(
            APPLICATION_NAME,
            APPLICATION_VERSION,
            ACTIVE_PROFILE,
            STARTUP_TIMEOUT,
            SHUTDOWN_TIMEOUT
    );

    private RuntimeConfigurationKeys() {}
}
