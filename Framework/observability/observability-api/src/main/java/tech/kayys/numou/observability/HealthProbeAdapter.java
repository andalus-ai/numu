package tech.kayys.syirkah.observability;

import io.smallrye.mutiny.Uni;

import java.time.Duration;

/**
 * Adapter allowing a legacy {@link HealthProbe} to be registered as a canonical {@link HealthCheck}.
 */
public final class HealthProbeAdapter implements HealthCheck {

    private final HealthProbe probe;
    private final HealthCheckCategory category;
    private final HealthCheckCriticality criticality;

    public HealthProbeAdapter(HealthProbe probe) {
        this(probe, HealthCheckCategory.RUNTIME, HealthCheckCriticality.REQUIRED);
    }

    public HealthProbeAdapter(HealthProbe probe, HealthCheckCategory category, HealthCheckCriticality criticality) {
        this.probe = java.util.Objects.requireNonNull(probe, "probe cannot be null");
        this.category = category != null ? category : HealthCheckCategory.RUNTIME;
        this.criticality = criticality != null ? criticality : HealthCheckCriticality.REQUIRED;
    }

    @Override
    public String id() {
        return probe.name();
    }

    @Override
    public String name() {
        return probe.name();
    }

    @Override
    public HealthCheckCategory category() {
        return category;
    }

    @Override
    public HealthCheckCriticality criticality() {
        return criticality;
    }

    @Override
    public Uni<HealthCheckResult> check() {
        long start = System.currentTimeMillis();
        return probe.probe().map(status -> {
            Duration elapsed = Duration.ofMillis(System.currentTimeMillis() - start);
            return new HealthCheckResult(
                    probe.name(),
                    status.status(),
                    status.detail(),
                    java.time.Instant.now(),
                    elapsed
            );
        });
    }
}
