package tech.kayys.numou.survey.bootstrap;

import io.quarkus.runtime.StartupEvent;
import tech.kayys.numou.survey.application.port.in.TenantAdminUseCase;
import tech.kayys.numou.survey.domain.DomainException;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.survey.domain.tenant.Plan;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Registers a "demo" tenant (PRO plan) on startup so the API can be tried right away. Disable in real environments. */
@ApplicationScoped
public class DevSeed {
    @Inject TenantAdminUseCase admin;
    @ConfigProperty(name = "survey.seed-demo-tenant", defaultValue = "false") boolean enabled;

    void onStart(@Observes StartupEvent ev) {
        if (!enabled) return;
        try {
            TenantId demoId = TenantId.of(java.util.UUID.nameUUIDFromBytes("demo".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            admin.register(new TenantAdminUseCase.RegisterTenant(demoId, "Demo Tenant", Plan.PRO));
        } catch (DomainException ignored) {
            // already there
        }
    }
}
