package tech.kayys.numou.commerce.marketplace.application.checkout;

import tech.kayys.numou.commerce.marketplace.domain.MarketplaceOrderLine;

import java.math.BigDecimal;
import java.time.Instant;

/** Anti-corruption port to inventory; implementations must use durable idempotent reservations. */
public interface MarketplaceCheckoutInventoryPort {
    String reserve(String tenantId, String warehouseReference, MarketplaceOrderLine line,
                   BigDecimal quantity, Instant expiresAt, String idempotencyKey);
    void release(String tenantId, String reservationId, String idempotencyKey);
}
