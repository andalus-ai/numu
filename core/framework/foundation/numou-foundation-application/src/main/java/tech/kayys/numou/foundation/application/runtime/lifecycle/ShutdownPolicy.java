package tech.kayys.numou.foundation.application.runtime.lifecycle;

import java.time.Duration;
import java.util.Objects;

/**
 * Policy governing timeout deadlines for each phase of graceful shutdown (config02.md §P4-08 #11).
 */
public record ShutdownPolicy(
        Duration totalTimeout,
        Duration admissionTimeout,
        Duration consumerTimeout,
        Duration dispatcherTimeout,
        Duration drainTimeout,
        Duration moduleTimeout,
        Duration infrastructureTimeout,
        boolean forceOnTimeout
) {
    public ShutdownPolicy {
        totalTimeout = totalTimeout != null ? totalTimeout : Duration.ofSeconds(30);
        admissionTimeout = admissionTimeout != null ? admissionTimeout : Duration.ofSeconds(3);
        consumerTimeout = consumerTimeout != null ? consumerTimeout : Duration.ofSeconds(5);
        dispatcherTimeout = dispatcherTimeout != null ? dispatcherTimeout : Duration.ofSeconds(5);
        drainTimeout = drainTimeout != null ? drainTimeout : Duration.ofSeconds(15);
        moduleTimeout = moduleTimeout != null ? moduleTimeout : Duration.ofSeconds(5);
        infrastructureTimeout = infrastructureTimeout != null ? infrastructureTimeout : Duration.ofSeconds(5);
    }

    public static ShutdownPolicy defaultPolicy() {
        return new ShutdownPolicy(
                Duration.ofSeconds(30),
                Duration.ofSeconds(2),
                Duration.ofSeconds(5),
                Duration.ofSeconds(5),
                Duration.ofSeconds(15),
                Duration.ofSeconds(5),
                Duration.ofSeconds(5),
                true
        );
    }
}
