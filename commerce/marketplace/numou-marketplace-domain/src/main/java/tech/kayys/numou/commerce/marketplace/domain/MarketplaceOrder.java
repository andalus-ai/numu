package tech.kayys.numou.commerce.marketplace.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Industry-neutral buyer/seller commercial order.
 *
 * <p>This aggregate owns the order lifecycle and accepted line snapshots only.
 * It does not own inventory, payment, fulfillment, shipment, or seller authorization.
 * Those capabilities are coordinated by an application service through public ports.</p>
 */
public final class MarketplaceOrder {
    private final UUID id;
    private final String tenantId;
    private final String buyerId;
    private final List<MarketplaceOrderLine> lines;
    private final String currency;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final MarketplaceOrderStatus status;
    private final String cancellationReason;

    private MarketplaceOrder(UUID id, String tenantId, String buyerId,
                             List<MarketplaceOrderLine> lines, String currency,
                             Instant createdAt, Instant updatedAt,
                             MarketplaceOrderStatus status, String cancellationReason) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.tenantId = required(tenantId, "tenantId");
        this.buyerId = required(buyerId, "buyerId");
        this.lines = List.copyOf(Objects.requireNonNull(lines, "lines are required"));
        if (this.lines.isEmpty()) throw new IllegalArgumentException("Order must contain at least one line");
        this.currency = required(currency, "currency").toUpperCase(java.util.Locale.ROOT);
        if (!this.currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("currency must be a three-letter code");
        for (MarketplaceOrderLine line : this.lines) {
            if (!this.currency.equals(line.currency())) {
                throw new IllegalArgumentException("All order lines must use the order currency");
            }
        }
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt cannot precede createdAt");
        this.status = Objects.requireNonNull(status, "status is required");
        this.cancellationReason = cancellationReason;
        if (status == MarketplaceOrderStatus.CANCELLED) required(cancellationReason, "cancellationReason");
    }

    public static MarketplaceOrder create(UUID id, String tenantId, String buyerId,
                                          List<MarketplaceOrderLine> lines, String currency,
                                          Instant now) {
        return new MarketplaceOrder(id, tenantId, buyerId, lines, currency, now, now,
                MarketplaceOrderStatus.CREATED, null);
    }

    /**
     * Reconstitutes a persisted aggregate without replaying lifecycle commands.
     * Persistence adapters must still validate tenant scope and stored data integrity.
     */
    public static MarketplaceOrder reconstitute(UUID id, String tenantId, String buyerId,
                                                 List<MarketplaceOrderLine> lines, String currency,
                                                 Instant createdAt, Instant updatedAt,
                                                 MarketplaceOrderStatus status, String cancellationReason) {
        return new MarketplaceOrder(id, tenantId, buyerId, lines, currency,
                createdAt, updatedAt, status, cancellationReason);
    }

    public MarketplaceOrder submit(Instant now) {
        requireStatus(MarketplaceOrderStatus.CREATED);
        return revise(MarketplaceOrderStatus.SUBMITTED, now, null);
    }

    /** Records seller acceptance of the commercial request; it does not assert stock/payment success. */
    public MarketplaceOrder sellerAccept(Instant now) {
        requireStatus(MarketplaceOrderStatus.SUBMITTED);
        return revise(MarketplaceOrderStatus.SELLER_ACCEPTED, now, null);
    }

    public MarketplaceOrder sellerReject(Instant now) {
        requireStatus(MarketplaceOrderStatus.SUBMITTED);
        return revise(MarketplaceOrderStatus.SELLER_REJECTED, now, null);
    }

    /** Called only after orchestration policy confirms required prerequisites. */
    public MarketplaceOrder requestFulfillment(Instant now) {
        requireStatus(MarketplaceOrderStatus.SELLER_ACCEPTED);
        return revise(MarketplaceOrderStatus.FULFILLMENT_REQUESTED, now, null);
    }

    public MarketplaceOrder cancel(String reason, Instant now) {
        if (status != MarketplaceOrderStatus.CREATED
                && status != MarketplaceOrderStatus.SUBMITTED
                && status != MarketplaceOrderStatus.SELLER_ACCEPTED) {
            throw new IllegalStateException("Order cannot be cancelled from " + status);
        }
        return revise(MarketplaceOrderStatus.CANCELLED, now, required(reason, "reason"));
    }

    public MarketplaceOrder complete(Instant now) {
        requireStatus(MarketplaceOrderStatus.FULFILLMENT_REQUESTED);
        return revise(MarketplaceOrderStatus.COMPLETED, now, null);
    }

    private MarketplaceOrder revise(MarketplaceOrderStatus next, Instant now, String reason) {
        Objects.requireNonNull(now, "now is required");
        if (now.isBefore(updatedAt)) throw new IllegalArgumentException("Transition time cannot precede updatedAt");
        return new MarketplaceOrder(id, tenantId, buyerId, lines, currency, createdAt, now, next, reason);
    }

    private void requireStatus(MarketplaceOrderStatus expected) {
        if (status != expected) throw new IllegalStateException("Expected " + expected + " but order is " + status);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    public UUID id() { return id; }
    public String tenantId() { return tenantId; }
    public String buyerId() { return buyerId; }
    public List<MarketplaceOrderLine> lines() { return lines; }
    public String currency() { return currency; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public MarketplaceOrderStatus status() { return status; }
    public String cancellationReason() { return cancellationReason; }
}
