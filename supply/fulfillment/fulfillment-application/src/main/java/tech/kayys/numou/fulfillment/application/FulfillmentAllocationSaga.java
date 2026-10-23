package tech.kayys.numou.fulfillment.application;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Durable progress record for inventory allocation and compensation. */
public record FulfillmentAllocationSaga(
        String tenantId,
        String workflowKey,
        String orderId,
        Instant reservationExpiresAt,
        AllocationSagaStatus status,
        Map<String, String> reservationIdsByLine,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public FulfillmentAllocationSaga {
        tenantId = requireText(tenantId, "tenantId");
        workflowKey = requireText(workflowKey, "workflowKey");
        orderId = requireText(orderId, "orderId");
        Objects.requireNonNull(reservationExpiresAt, "reservationExpiresAt is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(reservationIdsByLine, "reservationIdsByLine is required");
        reservationIdsByLine = Map.copyOf(reservationIdsByLine);
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (version < 1) throw new IllegalArgumentException("version must be positive");
    }

    public static FulfillmentAllocationSaga start(String tenantId, String workflowKey, String orderId,
                                                   Instant reservationExpiresAt) {
        if (!reservationExpiresAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException("Reservation expiry must be in the future when starting an allocation workflow");
        }
        Instant now = Instant.now();
        return new FulfillmentAllocationSaga(tenantId, workflowKey, orderId, reservationExpiresAt,
                AllocationSagaStatus.RESERVING, Map.of(), now, now, 1);
    }

    public FulfillmentAllocationSaga withReservation(String lineId, String reservationId) {
        if (status != AllocationSagaStatus.RESERVING) {
            throw new IllegalStateException("Cannot add reservations while saga is " + status);
        }
        String line = requireText(lineId, "lineId");
        String reservation = requireText(reservationId, "reservationId");
        String existing = reservationIdsByLine.get(line);
        if (existing != null) {
            if (!existing.equals(reservation)) throw new IllegalStateException("Reservation changed for line " + line);
            return this;
        }
        Map<String, String> next = new LinkedHashMap<>(reservationIdsByLine);
        next.put(line, reservation);
        return revise(status, next);
    }

    public FulfillmentAllocationSaga transition(AllocationSagaStatus nextStatus) {
        Objects.requireNonNull(nextStatus, "nextStatus is required");
        boolean allowed = switch (status) {
            case RESERVING -> nextStatus == AllocationSagaStatus.RESERVATIONS_READY
                    || nextStatus == AllocationSagaStatus.COMPENSATION_REQUIRED;
            case RESERVATIONS_READY -> nextStatus == AllocationSagaStatus.CONFIRMING
                    || nextStatus == AllocationSagaStatus.COMPENSATION_REQUIRED;
            case CONFIRMING -> nextStatus == AllocationSagaStatus.COMPLETED;
            case COMPENSATION_REQUIRED -> nextStatus == AllocationSagaStatus.COMPENSATED;
            case COMPLETED, COMPENSATED -> false;
        };
        if (!allowed) throw new IllegalStateException("Invalid allocation saga transition " + status + " -> " + nextStatus);
        return revise(nextStatus, reservationIdsByLine);
    }

    private FulfillmentAllocationSaga revise(AllocationSagaStatus nextStatus, Map<String, String> nextReservations) {
        return new FulfillmentAllocationSaga(tenantId, workflowKey, orderId, reservationExpiresAt,
                nextStatus, nextReservations, createdAt, Instant.now(), version + 1);
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label + " is required");
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + " must not be blank");
        return normalized;
    }
}
