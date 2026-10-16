package tech.kayys.syirkah.observability;

import io.smallrye.mutiny.Uni;

/**
 * A probe an application exposes.
 *
 * <p>Kept as a plain interface so Quarkus SmallRye Health, a custom
 * HTTP handler or a test double can all satisfy it without the
 * application knowing which.
 */
public interface HealthProbe {

    String name();

    Uni<HealthStatus> probe();
}
