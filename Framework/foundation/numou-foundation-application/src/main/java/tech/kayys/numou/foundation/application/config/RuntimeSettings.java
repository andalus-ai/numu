package tech.kayys.numou.foundation.application.config;

import java.time.Duration;

/**
 * Strongly-typed runtime settings projected from Configuration.
 */
public interface RuntimeSettings {

    String applicationName();

    String applicationVersion();

    String activeProfile();

    Duration startupTimeout();

    Duration shutdownTimeout();
}
