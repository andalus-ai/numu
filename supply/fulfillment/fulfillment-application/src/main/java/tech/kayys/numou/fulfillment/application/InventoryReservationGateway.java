package tech.kayys.numou.fulfillment.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Anti-corruption port to inventory. Adapters map neutral item/warehouse
 * references to inventory identifiers and use tenant-scoped, durable command
 * idempotency. This port never writes stock movements directly.
 */
public interface InventoryReservationGateway {
    String reserve(String tenantId, String itemReference, String warehouseReference,
                   BigDecimal quantity, String uom, String sourceReference,
                   Instant expiresAt, String idempotencyKey);

    Optional<String> findReservationIdByCommandKey(String tenantId, String idempotencyKey);

    void release(String tenantId, String reservationId, String idempotencyKey);
}
