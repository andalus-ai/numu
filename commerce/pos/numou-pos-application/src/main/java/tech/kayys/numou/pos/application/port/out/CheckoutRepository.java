package tech.kayys.numou.pos.application.port.out;

import tech.kayys.numou.pos.domain.CheckoutSession;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped durable checkout state with optimistic concurrency. */
public interface CheckoutRepository {
    Optional<CheckoutSession> find(String tenantId, UUID checkoutId);
    CheckoutSession create(CheckoutSession session);
    /** Oldest non-terminal checkouts for one tenant; limit must be bounded by the adapter. */
    java.util.List<CheckoutSession> findRecoverable(String tenantId, int limit);
    /** Must atomically compare expectedVersion and reject stale updates. */
    CheckoutSession update(CheckoutSession session, long expectedVersion);
}
