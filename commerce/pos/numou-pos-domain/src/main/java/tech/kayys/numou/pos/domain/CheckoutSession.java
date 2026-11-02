package tech.kayys.numou.pos.domain;

import tech.kayys.numou.foundation.domain.valueobject.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Durable checkout saga state. Payment and inventory remain source-owned capabilities. */
public record CheckoutSession(UUID id, String tenantId, UUID saleId, Money amount, Status status,
        String reservationReference, String paymentId, Instant createdAt, Instant updatedAt, long version) {
    public enum Status { STOCK_PENDING, PAYMENT_PENDING, PAYMENT_UNKNOWN, PAID, COMPLETED, COMPENSATION_REQUIRED, CANCELLED }

    public CheckoutSession {
        Objects.requireNonNull(id); tenantId = required(tenantId, "tenantId");
        Objects.requireNonNull(saleId); Objects.requireNonNull(amount); Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt); Objects.requireNonNull(updatedAt);
        if (!amount.isPositive()) throw new IllegalArgumentException("checkout amount must be positive");
        if (updatedAt.isBefore(createdAt) || version < 0) throw new IllegalArgumentException("invalid checkout chronology/version");
        if (status != Status.STOCK_PENDING && reservationReference == null)
            throw new IllegalArgumentException("reservation reference required");
        if ((status == Status.PAYMENT_PENDING || status == Status.PAYMENT_UNKNOWN || status == Status.PAID || status == Status.COMPLETED)
                && paymentId == null) throw new IllegalArgumentException("payment id required");
    }
    public static CheckoutSession start(UUID id, String tenant, UUID sale, Money amount, Instant now) {
        return new CheckoutSession(id, tenant, sale, amount, Status.STOCK_PENDING, null, null, now, now, 0);
    }
    public CheckoutSession reserveStock(String ref, Instant now) {
        require(Status.STOCK_PENDING);
        return next(Status.PAYMENT_PENDING, required(ref, "reservationReference"), null, now);
    }
    public CheckoutSession attachPayment(String id, Instant now) {
        if (status != Status.PAYMENT_PENDING && status != Status.PAYMENT_UNKNOWN) throw new IllegalStateException("payment cannot be attached in " + status);
        return next(Status.PAYMENT_PENDING, reservationReference, required(id, "paymentId"), now);
    }
    public CheckoutSession markUnknown(Instant now) {
        if (status != Status.PAYMENT_PENDING && status != Status.PAYMENT_UNKNOWN) throw new IllegalStateException("invalid payment transition");
        return next(Status.PAYMENT_UNKNOWN, reservationReference, paymentId, now);
    }
    public CheckoutSession markPaid(Instant now) {
        if (status != Status.PAYMENT_PENDING && status != Status.PAYMENT_UNKNOWN) throw new IllegalStateException("invalid payment transition");
        return next(Status.PAID, reservationReference, paymentId, now);
    }
    public CheckoutSession complete(Instant now) {
        require(Status.PAID); return next(Status.COMPLETED, reservationReference, paymentId, now);
    }
    public CheckoutSession cancel(Instant now) {
        if (status != Status.PAYMENT_PENDING && status != Status.PAYMENT_UNKNOWN) throw new IllegalStateException("invalid cancellation transition");
        return next(Status.CANCELLED, reservationReference, paymentId, now);
    }
    private CheckoutSession next(Status s, String r, String p, Instant now) {
        if (now.isBefore(updatedAt)) throw new IllegalArgumentException("transition time cannot move backwards");
        return new CheckoutSession(id, tenantId, saleId, amount, s, r, p, createdAt, now, version + 1);
    }
    private void require(Status expected) { if (status != expected) throw new IllegalStateException("expected " + expected + " but was " + status); }
    private static String required(String v, String n) { if (v == null || v.isBlank()) throw new IllegalArgumentException(n + " cannot be blank"); return v.trim(); }
}
