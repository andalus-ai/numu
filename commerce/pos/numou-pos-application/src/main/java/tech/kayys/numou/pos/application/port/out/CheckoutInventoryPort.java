package tech.kayys.numou.pos.application.port.out;

import java.util.UUID;

/** Tenant-scoped inventory saga boundary; all methods must be durable and idempotent by key. */
public interface CheckoutInventoryPort {
    Reservation reserve(String tenantId, UUID checkoutId, UUID saleId, String idempotencyKey);
    /** Atomically consumes a reservation; replay with the same key returns the original result. */
    void commit(String tenantId, String reservationReference, String idempotencyKey);
    /** Releases only an active reservation; repeated calls are successful no-ops. */
    void release(String tenantId, String reservationReference, String idempotencyKey);
    record Reservation(String reference) {
        public Reservation { if (reference == null || reference.isBlank()) throw new IllegalArgumentException("reference cannot be blank"); reference = reference.trim(); }
    }
}
