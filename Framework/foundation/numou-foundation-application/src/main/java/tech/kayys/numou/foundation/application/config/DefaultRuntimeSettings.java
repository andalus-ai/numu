package tech.kayys.numou.foundation.application.config;

import java.time.Duration;
import java.util.Objects;

/**
 * Default implementation of RuntimeSettings reading from Configuration.
 */
public final class DefaultRuntimeSettings implements RuntimeSettings {

    private final String applicationName;
    private final String applicationVersion;
    private final String activeProfile;
    private final Duration startupTimeout;
    private final Duration shutdownTimeout;

    public DefaultRuntimeSettings(Configuration configuration) {
        Objects.requireNonNull(configuration, "configuration cannot be null");
        this.applicationName = configuration.require(RuntimeConfigurationKeys.APPLICATION_NAME);
        this.applicationVersion = configuration.require(RuntimeConfigurationKeys.APPLICATION_VERSION);
        this.activeProfile = configuration.get(RuntimeConfigurationKeys.ACTIVE_PROFILE).orElse("default");
        this.startupTimeout = configuration.get(RuntimeConfigurationKeys.STARTUP_TIMEOUT).orElse(Duration.ofSeconds(60));
        this.shutdownTimeout = configuration.get(RuntimeConfigurationKeys.SHUTDOWN_TIMEOUT).orElse(Duration.ofSeconds(30));
    }

    public DefaultRuntimeSettings(
            String applicationName,
            String applicationVersion,
            String activeProfile,
            Duration startupTimeout,
            Duration shutdownTimeout) {
        this.applicationName = Objects.requireNonNull(applicationName, "applicationName cannot be null");
        this.applicationVersion = Objects.requireNonNull(applicationVersion, "applicationVersion cannot be null");
        this.activeProfile = Objects.requireNonNull(activeProfile, "activeProfile cannot be null");
        this.startupTimeout = Objects.requireNonNull(startupTimeout, "startupTimeout cannot be null");
        this.shutdownTimeout = Objects.requireNonNull(shutdownTimeout, "shutdownTimeout cannot be null");
    }

    @Override
    public String applicationName() {
        return applicationName;
    }

    @Override
    public String applicationVersion() {
        return applicationVersion;
    }

    @Override
    public String activeProfile() {
        return activeProfile;
    }

    @Override
    public Duration startupTimeout() {
        return startupTimeout;
    }

    @Override
    public Duration shutdownTimeout() {
        return shutdownTimeout;
    }
}
