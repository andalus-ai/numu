package tech.kayys.numou.pos.application.port.out;

import tech.kayys.numou.pos.domain.CheckoutSession;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped durable checkout state with optimistic concurrency. */
public interface CheckoutRepository {
    Optional<CheckoutSession> find(String tenantId, UUID checkoutId);
    CheckoutSession create(CheckoutSession session);
    /** Must atomically compare expectedVersion and reject stale updates. */
    CheckoutSession update(CheckoutSession session, long expectedVersion);
}
