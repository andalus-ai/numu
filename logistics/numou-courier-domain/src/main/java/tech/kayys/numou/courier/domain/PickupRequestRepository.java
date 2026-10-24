package tech.kayys.numou.courier.domain;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
/** Tenant-scoped persistence contract for first-mile pickups. */
public interface PickupRequestRepository {
 CompletionStage<PickupRequest> save(TenantId tenantId,PickupRequest request);
 CompletionStage<Optional<PickupRequest>> findById(TenantId tenantId,PickupRequestId id);
}