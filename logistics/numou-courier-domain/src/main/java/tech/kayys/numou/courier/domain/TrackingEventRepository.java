package tech.kayys.numou.courier.domain;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletionStage;

/** Tenant-explicit append-only tracking store. Implementations must never update/delete facts. */
public interface TrackingEventRepository {
    /** Repeated idempotency keys with the same fact return the original event; changed facts conflict. */
    CompletionStage<TrackingEvent> append(TenantId tenantId, TrackingEvent event);

    /** Events are ordered by occurred_at then event id; cursor is exclusive. */
    CompletionStage<List<TrackingEvent>> findByShipment(
            TenantId tenantId, ShipmentId shipmentId, Instant beforeOccurredAt, int limit);
}
