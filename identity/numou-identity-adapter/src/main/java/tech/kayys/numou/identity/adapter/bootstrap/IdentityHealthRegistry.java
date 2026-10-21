package tech.kayys.numou.identity.adapter.bootstrap;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import tech.kayys.numou.identity.adapter.outbound.health.DatabaseHealthProbe;
import tech.kayys.numou.identity.adapter.outbound.health.KafkaHealthProbe;
import tech.kayys.numou.observability.DefaultHealthCheckRegistry;
import tech.kayys.numou.observability.HealthCheckCategory;
import tech.kayys.numou.observability.HealthCheckCriticality;
import tech.kayys.numou.observability.HealthCheckRegistry;
import tech.kayys.numou.observability.HealthProbeAdapter;

/**
 * Wires the framework health registry: every probe this service exposes is registered at
 * startup (and the registry frozen, so late registrations - the usual sign of a bean that
 * failed to start - fail fast) and read back by {@code HealthResource}.
 */
@ApplicationScoped
public class IdentityHealthRegistry {

    @Produces
    @ApplicationScoped
    public HealthCheckRegistry healthCheckRegistry() {
        return new DefaultHealthCheckRegistry();
    }

    void registerProbes(
            @Observes StartupEvent event,
            HealthCheckRegistry registry,
            DatabaseHealthProbe database,
            KafkaHealthProbe kafka
    ) {
        registry.register(new HealthProbeAdapter(
                database, HealthCheckCategory.DATABASE, HealthCheckCriticality.REQUIRED));
        // OPTIONAL: an unreachable broker degrades readiness instead of failing liveness.
        registry.register(new HealthProbeAdapter(
                kafka, HealthCheckCategory.MESSAGE_BROKER, HealthCheckCriticality.OPTIONAL));
        registry.freeze();
    }
}
