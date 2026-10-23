package tech.kayys.numou.fulfillment.application;

import tech.kayys.numou.fulfillment.domain.FulfillmentLine;
import tech.kayys.numou.fulfillment.domain.FulfillmentOrder;
import tech.kayys.numou.fulfillment.domain.FulfillmentOrderId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reserves inventory for all fulfillment lines before confirming allocations.
 * Each external side effect has a stable key; saga progress is durable and
 * recoverable after process restarts. Adapters must make gateway operations
 * idempotent and persist saga state transactionally.
 */
public final class FulfillmentAllocationCoordinator {
    private final FulfillmentApplicationService fulfillment;
    private final FulfillmentRepository orders;
    private final InventoryReservationGateway inventory;
    private final FulfillmentAllocationSagaRepository sagas;

    public FulfillmentAllocationCoordinator(FulfillmentApplicationService fulfillment,
                                            FulfillmentRepository orders,
                                            InventoryReservationGateway inventory,
                                            FulfillmentAllocationSagaRepository sagas) {
        this.fulfillment = Objects.requireNonNull(fulfillment);
        this.orders = Objects.requireNonNull(orders);
        this.inventory = Objects.requireNonNull(inventory);
        this.sagas = Objects.requireNonNull(sagas);
    }

    public FulfillmentOrder allocate(String tenantId, FulfillmentOrderId orderId,
                                     String warehouseReference, Instant expiresAt,
                                     FulfillmentCommandKey workflowKey) {
        String tenant = requireText(tenantId, "tenantId");
        String warehouse = requireText(warehouseReference, "warehouseReference");
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(expiresAt, "expiresAt is required");
        Objects.requireNonNull(workflowKey, "workflowKey is required");

        FulfillmentOrder order = orders.find(tenant, orderId)
                .orElseThrow(() -> new IllegalArgumentException("Fulfillment order not found for tenant"));
        Optional<FulfillmentAllocationSaga> existingSaga = sagas.find(tenant, workflowKey.value());
        if (existingSaga.isEmpty() && order.lines().stream().anyMatch(line -> line.allocatedQuantity().signum() > 0)) {
            throw new IllegalStateException("A new allocation workflow cannot overwrite existing allocations");
        }
        FulfillmentAllocationSaga saga = sagas.beginOrLoad(tenant, workflowKey.value(), orderId.value(), expiresAt);
        if (!saga.orderId().equals(orderId.value())) throw new IllegalStateException("Workflow key belongs to another order");
        if (saga.status() == AllocationSagaStatus.COMPLETED) return order;
        if (saga.status() == AllocationSagaStatus.COMPENSATED) {
            throw new IllegalStateException("This allocation attempt was compensated; start a new workflow with a new key");
        }
        if (saga.status() == AllocationSagaStatus.COMPENSATION_REQUIRED) {
            compensate(saga, order.lines());
            throw new IllegalStateException("Allocation attempt failed and was compensated");
        }

        if (saga.status() == AllocationSagaStatus.RESERVING) {
            try {
                for (FulfillmentLine line : order.lines()) {
                    if (line.allocatedQuantity().signum() > 0) {
                        throw new IllegalStateException("Allocation changed while reservation saga was in progress");
                    }
                    if (saga.reservationIdsByLine().containsKey(line.lineId())) continue;
                    String reserveKey = reserveKey(tenant, workflowKey.value(), line.lineId());
                    String reservationId = inventory.reserve(tenant, line.itemReference(), warehouse,
                            line.requestedQuantity(), line.uom(), orderId.value() + ":" + line.lineId(),
                            saga.reservationExpiresAt(), reserveKey);
                    if (reservationId == null || reservationId.isBlank()) {
                        throw new IllegalStateException("Inventory adapter returned a blank reservation id");
                    }
                    FulfillmentAllocationSaga next = saga.withReservation(line.lineId(), reservationId);
                    saga = sagas.save(next, saga.version());
                }
                saga = persistTransition(saga, AllocationSagaStatus.RESERVATIONS_READY);
            } catch (RuntimeException reservationFailure) {
                try {
                    saga = persistTransition(saga, AllocationSagaStatus.COMPENSATION_REQUIRED);
                    compensate(saga, order.lines());
                } catch (RuntimeException compensationFailure) {
                    reservationFailure.addSuppressed(compensationFailure);
                }
                throw reservationFailure;
            }
        }

        if (saga.status() == AllocationSagaStatus.RESERVATIONS_READY) {
            saga = persistTransition(saga, AllocationSagaStatus.CONFIRMING);
        }
        if (saga.status() == AllocationSagaStatus.CONFIRMING) {
            // Stable command keys make every line safe to replay after partial progress.
            FulfillmentOrder latest = orders.find(tenant, orderId)
                    .orElseThrow(() -> new IllegalStateException("Fulfillment order disappeared during allocation"));
            for (FulfillmentLine originalLine : latest.lines()) {
                if (originalLine.allocatedQuantity().compareTo(originalLine.requestedQuantity()) == 0) continue;
                if (originalLine.allocatedQuantity().signum() > 0) {
                    throw new IllegalStateException("Partial external allocation conflicts with this allocation workflow");
                }
                fulfillment.confirmAllocation(tenant, orderId, originalLine.lineId(), originalLine.requestedQuantity(),
                        warehouse, new FulfillmentCommandKey(allocationConfirmationKey(tenant, workflowKey.value(), originalLine.lineId())));
            }
            saga = persistTransition(saga, AllocationSagaStatus.COMPLETED);
        }
        return orders.find(tenant, orderId)
                .orElseThrow(() -> new IllegalStateException("Fulfillment order disappeared after allocation"));
    }

    private void compensate(FulfillmentAllocationSaga saga, List<FulfillmentLine> lines) {
        RuntimeException failure = null;
        for (FulfillmentLine line : lines) {
            String key = reserveKey(saga.tenantId(), saga.workflowKey(), line.lineId());
            String reservationId = saga.reservationIdsByLine().get(line.lineId());
            if (reservationId == null) {
                try {
                    reservationId = inventory.findReservationIdByCommandKey(saga.tenantId(), key).orElse(null);
                } catch (RuntimeException lookupFailure) {
                    if (failure == null) failure = lookupFailure;
                    else failure.addSuppressed(lookupFailure);
                    continue;
                }
            }
            if (reservationId == null) continue;
            try {
                inventory.release(saga.tenantId(), reservationId,
                        releaseKey(saga.tenantId(), saga.workflowKey(), line.lineId()));
            } catch (RuntimeException releaseFailure) {
                if (failure == null) failure = releaseFailure;
                else failure.addSuppressed(releaseFailure);
            }
        }
        if (failure != null) throw failure;
        if (saga.status() == AllocationSagaStatus.COMPENSATION_REQUIRED) {
            persistTransition(saga, AllocationSagaStatus.COMPENSATED);
        }
    }

    private FulfillmentAllocationSaga persistTransition(FulfillmentAllocationSaga saga, AllocationSagaStatus status) {
        FulfillmentAllocationSaga next = saga.transition(status);
        return sagas.save(next, saga.version());
    }

    private static String reserveKey(String tenant, String workflowKey, String lineId) {
        return "fulfillment-reserve:" + digest(tenant, workflowKey, lineId);
    }

    private static String releaseKey(String tenant, String workflowKey, String lineId) {
        return "fulfillment-release:" + digest(tenant, workflowKey, lineId);
    }

    private static String allocationConfirmationKey(String tenant, String workflowKey, String lineId) {
        return "fulfillment-allocation:" + digest(tenant, workflowKey, lineId);
    }

    private static String digest(String... values) {
        StringBuilder canonical = new StringBuilder();
        for (String value : values) canonical.append(value.length()).append(':').append(value);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", impossible);
        }
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label + " is required");
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + " must not be blank");
        return normalized;
    }
}
