package tech.kayys.syirkah.observability;

import java.util.Collection;

/**
 * Standard aggregation logic for health check results (config02.md §P4-07 #9).
 */
public final class HealthAggregator {

    private HealthAggregator() {}

    public static HealthStatus.Status aggregate(Collection<HealthCheckResult> results) {
        if (results == null || results.isEmpty()) {
            return HealthStatus.Status.UP;
        }

        boolean hasDown = false;
        boolean hasDegraded = false;

        for (HealthCheckResult res : results) {
            if (res.status() == HealthStatus.Status.DOWN) {
                hasDown = true;
                break;
            } else if (res.status() == HealthStatus.Status.DEGRADED) {
                hasDegraded = true;
            }
        }

        if (hasDown) {
            return HealthStatus.Status.DOWN;
        }
        if (hasDegraded) {
            return HealthStatus.Status.DEGRADED;
        }
        return HealthStatus.Status.UP;
    }
}
