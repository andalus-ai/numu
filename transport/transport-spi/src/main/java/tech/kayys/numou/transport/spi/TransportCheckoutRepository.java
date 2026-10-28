package tech.kayys.numou.transport.spi;

import tech.kayys.numou.transport.domain.TransportCheckout;

import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped durable checkout correlation with compare-and-set updates. */
public interface TransportCheckoutRepository {
    Optional<TransportCheckout> find(String tenantId, UUID checkoutId);
    Optional<TransportCheckout> findByIdempotencyKey(String tenantId, String idempotencyKey);
    Optional<TransportCheckout> findByPaymentId(String tenantId, String paymentId);

    /** Creates a new REQUESTED checkout. Duplicate tenant/idempotency keys are conflicts. */
    void create(TransportCheckout checkout);

    /** Atomically saves the new snapshot only when the persisted version equals expectedVersion. */
    void save(TransportCheckout checkout, int expectedVersion);
}
