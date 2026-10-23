package tech.kayys.numou.fulfillment.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class InMemoryInventoryReservationGateway implements InventoryReservationGateway {
    private final Map<String, String> reservationsByCommand = new HashMap<>();
    private final Set<String> released = new HashSet<>();
    private String failItemReference;

    void failForItem(String itemReference) {
        failItemReference = itemReference;
    }

    @Override
    public synchronized String reserve(String tenantId, String itemReference, String warehouseReference,
                                       BigDecimal quantity, String uom, String sourceReference,
                                       Instant expiresAt, String idempotencyKey) {
        String key = key(tenantId, idempotencyKey);
        if (reservationsByCommand.containsKey(key)) return reservationsByCommand.get(key);
        if (itemReference.equals(failItemReference)) throw new IllegalStateException("simulated insufficient stock");
        String id = "reservation-" + reservationsByCommand.size();
        reservationsByCommand.put(key, id);
        return id;
    }

    @Override
    public synchronized Optional<String> findReservationIdByCommandKey(String tenantId, String idempotencyKey) {
        return Optional.ofNullable(reservationsByCommand.get(key(tenantId, idempotencyKey)));
    }

    @Override
    public synchronized void release(String tenantId, String reservationId, String idempotencyKey) {
        released.add(key(tenantId, idempotencyKey) + ":" + reservationId);
    }

    synchronized int releasedCount() {
        return released.size();
    }

    synchronized int reservationCount() {
        return reservationsByCommand.size();
    }

    private static String key(String tenant, String command) {
        return tenant.length() + ":" + tenant + command;
    }
}
