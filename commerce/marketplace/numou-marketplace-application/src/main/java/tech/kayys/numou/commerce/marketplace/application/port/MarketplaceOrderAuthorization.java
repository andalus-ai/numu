package tech.kayys.numou.commerce.marketplace.application.port;

import tech.kayys.numou.commerce.marketplace.domain.MarketplaceOrder;

/**
 * Authorization boundary. Implementations must derive permissions from trusted
 * identity and membership data, not from identifiers supplied by the caller.
 */
public interface MarketplaceOrderAuthorization {
    void requireBuyer(String tenantId, String actorId, MarketplaceOrder order);

    /** For trusted workflow/service actors allowed to advance fulfillment. */
    void requireFulfillmentOperator(String tenantId, String actorId, MarketplaceOrder order);
}