package tech.kayys.numou.courier.application.command;

import tech.kayys.numou.courier.domain.CourierRoutePlan;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.util.Objects;

/** Command to persist a route-plan version under an authenticated actor and tenant. */
public record SaveCourierRoutePlanCommand(TenantId tenantId, String actorReference, CourierRoutePlan routePlan) {
    public SaveCourierRoutePlanCommand {
        Objects.requireNonNull(tenantId); Objects.requireNonNull(routePlan); Objects.requireNonNull(actorReference);
        actorReference=actorReference.trim();
        if(actorReference.isEmpty()) throw new IllegalArgumentException("actorReference must not be blank");
        if(!tenantId.equals(routePlan.tenantId())) throw new SecurityException("route plan tenant mismatch");
    }
}
