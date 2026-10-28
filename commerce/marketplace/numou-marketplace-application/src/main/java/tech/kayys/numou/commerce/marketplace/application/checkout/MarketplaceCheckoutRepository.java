package tech.kayys.numou.commerce.marketplace.application.checkout;

import java.util.Optional;
import java.util.UUID;

/** Durable tenant-scoped checkout state. Implementations must use atomic database constraints/CAS. */
public interface MarketplaceCheckoutRepository {
    /** Atomically starts or returns a checkout for an identical tenant-scoped key and fingerprint. */
    MarketplaceCheckout createOrGet(MarketplaceCheckout proposed);
    Optional<MarketplaceCheckout> findByTenantAndId(String tenantId, UUID checkoutId);
    Optional<MarketplaceCheckout> findByTenantAndKey(String tenantId, String idempotencyKey);
    /** Oldest non-terminal checkouts that can be reconciled without guessing an inventory outcome. */
    java.util.List<MarketplaceCheckout> findRecoverable(String tenantId, int limit);
    /** Atomically persists the snapshot only when the stored version equals expectedVersion. */
    boolean saveIfVersion(String tenantId, UUID checkoutId, long expectedVersion, MarketplaceCheckout replacement);
}
