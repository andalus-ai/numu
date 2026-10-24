package tech.kayys.numou.courier.application.port;

import tech.kayys.numou.courier.domain.ShipmentId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.util.concurrent.CompletionStage;

/** Authorization boundary; implementations must validate actor membership and branch scope, not trust tenant IDs alone. */
public interface CourierPlanningAccess {
    CompletionStage<Void> assertCanEstimateEta(TenantId tenantId, ShipmentId shipmentId, String actorReference);
    CompletionStage<Void> assertCanManageRoutePlan(TenantId tenantId, String branchCode, String actorReference);
}
