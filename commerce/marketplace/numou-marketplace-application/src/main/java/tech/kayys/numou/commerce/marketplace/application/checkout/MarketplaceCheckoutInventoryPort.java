package tech.kayys.numou.commerce.marketplace.application.checkout;

import tech.kayys.numou.commerce.marketplace.domain.MarketplaceOrderLine;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/** Anti-corruption port to inventory; implementations must use durable idempotent reservations. */
public interface MarketplaceCheckoutInventoryPort {
    /** The inventory capability has authoritatively rejected the reservation; no reservation was created. */
    final class ReservationRejectedException extends RuntimeException {
        public ReservationRejectedException(String message) { super(message); }
    }

    /** The reservation command may have committed, but its outcome could not be confirmed. */
    final class ReservationOutcomeUnknownException extends RuntimeException {
        public ReservationOutcomeUnknownException(String message, Throwable cause) { super(message, cause); }
    }

    String reserve(String tenantId, String warehouseReference, MarketplaceOrderLine line,
                   BigDecimal quantity, Instant expiresAt, String idempotencyKey);
    Optional<String> findReservationIdByCommandKey(String tenantId, String idempotencyKey);
    void release(String tenantId, String reservationId, String idempotencyKey);
}
