package tech.kayys.numou.commerce.marketplace.application;

import tech.kayys.numou.commerce.marketplace.application.port.IdempotentMarketplaceOrderRepository;
import tech.kayys.numou.commerce.marketplace.application.port.MarketplaceOrderAuthorization;
import tech.kayys.numou.commerce.marketplace.domain.MarketplaceOrder;
import tech.kayys.numou.commerce.marketplace.domain.MarketplaceOrderLine;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Retry-safe order creation for checkout entry points. It validates buyer access
 * before persistence and delegates atomic key/order creation to a durable adapter.
 * Inventory and payment are deliberately separate saga steps.
 */
public final class IdempotentMarketplaceOrderCreationService {
    private final IdempotentMarketplaceOrderRepository orders;
    private final MarketplaceOrderAuthorization authorization;
    private final Clock clock;

    public IdempotentMarketplaceOrderCreationService(IdempotentMarketplaceOrderRepository orders,
                                                     MarketplaceOrderAuthorization authorization,
                                                     Clock clock) {
        this.orders = Objects.requireNonNull(orders, "orders is required");
        this.authorization = Objects.requireNonNull(authorization, "authorization is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    public MarketplaceOrder create(String tenantId, String actorId, String buyerId,
                                   String idempotencyKey, List<MarketplaceOrderLine> lines,
                                   String currency) {
        require(tenantId, "tenantId");
        require(actorId, "actorId");
        require(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(lines, "lines are required");
        if (lines.isEmpty()) throw new IllegalArgumentException("Order must contain at least one line");
        String sellerId = lines.get(0).sellerId();
        if (lines.stream().anyMatch(line -> !sellerId.equals(line.sellerId()))) {
            throw new IllegalArgumentException("A marketplace order must contain lines from exactly one seller");
        }

        MarketplaceOrder proposed = MarketplaceOrder.create(
                UUID.randomUUID(), tenantId, buyerId, lines, currency, clock.instant());
        authorization.requireBuyer(tenantId, actorId, proposed);
        String fingerprint = MarketplaceOrderRequestFingerprint.calculate(tenantId, buyerId, currency, lines);
        MarketplaceOrder result = orders.createOrGet(tenantId, idempotencyKey, fingerprint, proposed);
        // Do not return a previously stored order until authorization has been
        // evaluated against the actual persisted aggregate as well.
        authorization.requireBuyer(tenantId, actorId, result);
        return result;
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }
}
