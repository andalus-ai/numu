package tech.kayys.numou.fulfillment.application;

import tech.kayys.numou.fulfillment.domain.FulfillmentOrder;
import tech.kayys.numou.fulfillment.domain.FulfillmentOrderId;

import java.util.Optional;

/**
 * Tenant-scoped persistence contract. Implementations must atomically commit
 * aggregate state, command receipt, and outbox events. Use optimistic version
 * checks and a unique key on (tenant_id, command_key); never rely on JVM locks.
 */
public interface FulfillmentRepository {
    Optional<FulfillmentOrder> find(String tenantId, FulfillmentOrderId orderId);

    Optional<FulfillmentCommandReceipt> findReceipt(String tenantId, FulfillmentCommandKey commandKey);

    /**
     * Creates or updates an order, persists the idempotency receipt and inserts
     * outbox events in one transaction. expectedVersion=0 means create-only.
     * On a concurrent same-key replay, return the persisted receipt result when
     * the fingerprint matches; reject it when the fingerprint differs.
     *
     * @throws FulfillmentConcurrencyException when expectedVersion no longer matches
     * @throws FulfillmentIdempotencyConflictException when a key is reused with another payload
     */
    FulfillmentCommitResult commit(String tenantId, long expectedVersion, FulfillmentOrder candidate,
                                   FulfillmentCommandKey commandKey, String requestFingerprint,
                                   java.util.List<FulfillmentOutboxEvent> outboxEvents);
}
