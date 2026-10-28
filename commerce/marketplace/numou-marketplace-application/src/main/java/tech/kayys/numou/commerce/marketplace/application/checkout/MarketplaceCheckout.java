package tech.kayys.numou.commerce.marketplace.application.checkout;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable checkout saga snapshot. Version is used for durable compare-and-set updates. */
public record MarketplaceCheckout(
        UUID id, String tenantId, UUID orderId, String idempotencyKey, String requestFingerprint,
        MarketplaceCheckoutStatus status, Map<String, String> reservationIds, String paymentId,
        String failureCode, String failureDetail, long version, Instant createdAt, Instant updatedAt) {

    public MarketplaceCheckout {
        Objects.requireNonNull(id, "id");
        tenantId = required(tenantId, "tenantId");
        Objects.requireNonNull(orderId, "orderId");
        idempotencyKey = required(idempotencyKey, "idempotencyKey");
        if (requestFingerprint == null || !requestFingerprint.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("requestFingerprint must be a SHA-256 hex digest");
        Objects.requireNonNull(status, "status");
        reservationIds = Map.copyOf(Objects.requireNonNull(reservationIds, "reservationIds"));
        if (reservationIds.entrySet().stream().anyMatch(e -> e.getKey().isBlank() || e.getValue().isBlank()))
            throw new IllegalArgumentException("reservation line and id must not be blank");
        if (version < 0) throw new IllegalArgumentException("version cannot be negative");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt cannot precede createdAt");
    }

    public static MarketplaceCheckout start(UUID id, String tenantId, UUID orderId, String key,
                                             String fingerprint, Instant now) {
        return new MarketplaceCheckout(id, tenantId, orderId, key, fingerprint,
                MarketplaceCheckoutStatus.RESERVING_INVENTORY, Map.of(), null, null, null, 0, now, now);
    }

    public MarketplaceCheckout recordReservation(String lineId, String reservationId, Instant now) {
        requireStatus(MarketplaceCheckoutStatus.RESERVING_INVENTORY);
        String existing = reservationIds.get(lineId);
        if (existing != null && !existing.equals(reservationId))
            throw new IllegalStateException("Reservation for an order line is immutable");
        var updated = new java.util.HashMap<>(reservationIds);
        updated.put(lineId, required(reservationId, "reservationId"));
        return revise(status, updated, paymentId, failureCode, failureDetail, now);
    }

    public MarketplaceCheckout awaitingPayment(String paymentId, Instant now) {
        if (status != MarketplaceCheckoutStatus.RESERVING_INVENTORY
                && status != MarketplaceCheckoutStatus.AWAITING_PAYMENT)
            throw new IllegalStateException("Cannot request payment from " + status);
        return revise(MarketplaceCheckoutStatus.AWAITING_PAYMENT, reservationIds,
                required(paymentId, "paymentId"), null, null, now);
    }

    public MarketplaceCheckout ready(Instant now) {
        if (status != MarketplaceCheckoutStatus.AWAITING_PAYMENT)
            throw new IllegalStateException("Cannot complete checkout from " + status);
        return revise(MarketplaceCheckoutStatus.READY_FOR_FULFILLMENT, reservationIds,
                paymentId, null, null, now);
    }

    public MarketplaceCheckout fail(String code, String detail, Instant now) {
        if (status == MarketplaceCheckoutStatus.READY_FOR_FULFILLMENT)
            throw new IllegalStateException("Completed checkout cannot fail");
        return revise(MarketplaceCheckoutStatus.FAILED, reservationIds, paymentId,
                required(code, "failureCode"), required(detail, "failureDetail"), now);
    }

    public MarketplaceCheckout compensationRequired(String code, String detail, Instant now) {
        return revise(MarketplaceCheckoutStatus.COMPENSATION_REQUIRED, reservationIds, paymentId,
                required(code, "failureCode"), required(detail, "failureDetail"), now);
    }

    public MarketplaceCheckout reconciliationRequired(String code, String detail, Instant now) {
        return revise(MarketplaceCheckoutStatus.RECONCILIATION_REQUIRED, reservationIds, paymentId,
                required(code, "failureCode"), required(detail, "failureDetail"), now);
    }

    public MarketplaceCheckout withVersion(long nextVersion) {
        return new MarketplaceCheckout(id, tenantId, orderId, idempotencyKey, requestFingerprint,
                status, reservationIds, paymentId, failureCode, failureDetail, nextVersion, createdAt, updatedAt);
    }

    private MarketplaceCheckout revise(MarketplaceCheckoutStatus next, Map<String, String> reservations,
                                       String payment, String code, String detail, Instant now) {
        if (now.isBefore(updatedAt)) throw new IllegalArgumentException("updatedAt cannot move backwards");
        return new MarketplaceCheckout(id, tenantId, orderId, idempotencyKey, requestFingerprint,
                next, reservations, payment, code, detail, version, createdAt, now);
    }

    private void requireStatus(MarketplaceCheckoutStatus expected) {
        if (status != expected) throw new IllegalStateException("Expected " + expected + " but was " + status);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
