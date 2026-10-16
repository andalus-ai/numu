package tech.kayys.syirkah.observability;

import io.smallrye.mutiny.Uni;

/**
 * Standard health check contract (config02.md §P4-07 #6).
 */
public interface HealthCheck {

    String id();

    String name();

    HealthCheckCategory category();

    HealthCheckCriticality criticality();

    Uni<HealthCheckResult> check();
}
