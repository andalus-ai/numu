package tech.kayys.numou.planning.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.planning.application.port.SchedulePlanScopeStore;
import tech.kayys.numou.planning.domain.PlanId;

import java.util.Objects;
import java.util.UUID;

/**
 * Registers an existing plan's authoritative tenant/structure scope.
 *
 * Call this only after the caller's authorization and tenant membership have been verified by
 * the application/API boundary. A plan's ownership is immutable through this use case.
 */
@ApplicationScoped
public class RegisterSchedulePlanScope {
    private final SchedulePlanScopeStore store;

    @Inject
    public RegisterSchedulePlanScope(SchedulePlanScopeStore store) {
        this.store = Objects.requireNonNull(store, "Scope store cannot be null");
    }

    public void execute(PlanId planId, UUID tenantId, UUID structureId) {
        Objects.requireNonNull(planId, "Plan id cannot be null");
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(structureId, "Structure id cannot be null");
        store.register(planId, tenantId, structureId);
    }
}
