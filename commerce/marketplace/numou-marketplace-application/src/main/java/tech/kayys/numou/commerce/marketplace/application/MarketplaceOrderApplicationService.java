package tech.kayys.numou.commerce.marketplace.application;

import tech.kayys.numou.commerce.marketplace.application.port.MarketplaceOrderAuthorization;
import tech.kayys.numou.commerce.marketplace.application.port.MarketplaceOrderRepository;
import tech.kayys.numou.commerce.marketplace.domain.MarketplaceOrder;
import tech.kayys.numou.commerce.marketplace.domain.MarketplaceOrderLine;
import tech.kayys.numou.commerce.marketplace.domain.MarketplaceOrderStatus;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Tenant-scoped use cases for the neutral marketplace order lifecycle.
 * Inventory, payment, fulfillment, and shipment remain separate capabilities;
 * this service does not claim their success or write their ledgers.
 */
public final class MarketplaceOrderApplicationService {
    private final MarketplaceOrderRepository orders;
    private final MarketplaceOrderAuthorization authorization;
    private final Clock clock;

    public MarketplaceOrderApplicationService(MarketplaceOrderRepository orders,
                                              MarketplaceOrderAuthorization authorization,
                                              Clock clock) {
        this.orders = Objects.requireNonNull(orders, "orders is required");
        this.authorization = Objects.requireNonNull(authorization, "authorization is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    public MarketplaceOrder create(String tenantId, String buyerId,
                                   List<MarketplaceOrderLine> lines, String currency) {
        Objects.requireNonNull(lines, "lines are required");
        MarketplaceOrder order = MarketplaceOrder.create(
                UUID.randomUUID(), tenantId, buyerId, lines, currency, clock.instant());
        authorization.requireBuyer(tenantId, buyerId, order);
        orders.create(order);
        return order;
    }

    public MarketplaceOrder submit(String tenantId, String actorId, UUID orderId) {
        MarketplaceOrder current = load(tenantId, orderId);
        authorization.requireBuyer(tenantId, actorId, current);
        return transition(tenantId, current, current.submit(clock.instant()));
    }

    public MarketplaceOrder cancel(String tenantId, String actorId, UUID orderId, String reason) {
        MarketplaceOrder current = load(tenantId, orderId);
        authorization.requireBuyer(tenantId, actorId, current);
        return transition(tenantId, current, current.cancel(reason, clock.instant()));
    }

    public MarketplaceOrder requestFulfillment(String tenantId, String actorId, UUID orderId) {
        MarketplaceOrder current = load(tenantId, orderId);
        authorization.requireFulfillmentOperator(tenantId, actorId, current);
        return transition(tenantId, current, current.requestFulfillment(clock.instant()));
    }

    public MarketplaceOrder complete(String tenantId, String actorId, UUID orderId) {
        MarketplaceOrder current = load(tenantId, orderId);
        authorization.requireFulfillmentOperator(tenantId, actorId, current);
        return transition(tenantId, current, current.complete(clock.instant()));
    }

    private MarketplaceOrder load(String tenantId, UUID orderId) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
        Objects.requireNonNull(orderId, "orderId is required");
        return orders.findByTenantAndId(tenantId, orderId)
                .orElseThrow(() -> new IllegalArgumentException("Marketplace order not found"));
    }

    private MarketplaceOrder transition(String tenantId, MarketplaceOrder current, MarketplaceOrder updated) {
        boolean saved = orders.replaceIfCurrentStatus(
                tenantId, current.id(), current.status(), updated);
        if (!saved) {
            throw new MarketplaceOrderConcurrentModificationException(
                    "Marketplace order changed concurrently; reload before retrying");
        }
        return updated;
    }
}