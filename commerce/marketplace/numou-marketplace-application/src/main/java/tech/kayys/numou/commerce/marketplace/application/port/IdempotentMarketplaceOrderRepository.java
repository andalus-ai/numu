package tech.kayys.numou.commerce.marketplace.application.port;

import tech.kayys.numou.commerce.marketplace.domain.MarketplaceOrder;

/**
 * Atomic idempotent order-creation contract. The idempotency claim, order header,
 * and immutable lines must commit or roll back together. Keys are tenant-scoped.
 */
public interface IdempotentMarketplaceOrderRepository {
    /**
     * Returns the newly created order or the previously committed order for an
     * identical request. A reused key with a different fingerprint must fail.
     */
    MarketplaceOrder createOrGet(String tenantId, String idempotencyKey,
                                 String requestFingerprint, MarketplaceOrder proposedOrder);
}
