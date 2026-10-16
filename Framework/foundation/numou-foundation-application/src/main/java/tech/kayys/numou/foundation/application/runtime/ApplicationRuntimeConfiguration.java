package tech.kayys.numou.foundation.application.runtime;

import java.time.Duration;
import java.util.Map;

/**
 * Configuration input for ApplicationRuntime initialization (config01.md §P4-01 #7).
 */
public interface ApplicationRuntimeConfiguration {

    String applicationName();

    String applicationVersion();

    String activeProfile();

    Duration startupTimeout();

    Duration shutdownTimeout();

    Map<String, String> properties();
}
