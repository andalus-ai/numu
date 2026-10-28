package tech.kayys.numou.commerce.marketplace.application.port;

import tech.kayys.numou.commerce.marketplace.domain.MarketplaceOrder;
import tech.kayys.numou.commerce.marketplace.domain.MarketplaceOrderStatus;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for marketplace orders.
 *
 * <p>Implementations must scope reads and transitions by tenant, create orders
 * atomically, and implement replaceIfCurrentStatus as a compare-and-set in the
 * same transaction as persistence. A process-local check followed by save is
 * not sufficient for multi-instance deployments.</p>
 */
public interface MarketplaceOrderRepository {
    /** Creates an order only if its identifier is not already present. */
    void create(MarketplaceOrder order);

    /** Never returns an order belonging to another tenant. */
    Optional<MarketplaceOrder> findByTenantAndId(String tenantId, UUID orderId);

    /**
     * Atomically replaces an order only if its persisted status still equals
     * expectedStatus. The implementation must also verify tenant and order id
     * match the replacement. Returns false on a concurrent transition.
     */
    boolean replaceIfCurrentStatus(String tenantId, UUID orderId,
                                   MarketplaceOrderStatus expectedStatus,
                                   MarketplaceOrder replacement);
}