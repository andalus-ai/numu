package tech.kayys.numou.transport.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/**
 * Durable correlation between a tenant-scoped transport hold and a payment obligation.
 * It does not represent an order, ticket, or proof of payment settlement.
 */
public record TransportCheckout(
        String tenantId,
        UUID checkoutId,
        String idempotencyKey,
        String requestFingerprint,
        UUID departureId,
        String quoteId,
        int fromSequence,
        int toSequence,
        int passengerCount,
        BigDecimal amount,
        String currency,
        Instant holdExpiresAt,
        UUID holdId,
        String paymentId,
        String paymentActionUri,
        String refundId,
        TransportCheckoutRefundStatus refundStatus,
        String refundReason,
        TransportCheckoutStatus status,
        int version,
        Instant createdAt,
        Instant updatedAt) {

    public TransportCheckout {
        tenantId = required(tenantId, "tenantId");
        Objects.requireNonNull(checkoutId, "checkoutId");
        idempotencyKey = required(idempotencyKey, "idempotencyKey");
        requestFingerprint = required(requestFingerprint, "requestFingerprint");
        Objects.requireNonNull(departureId, "departureId");
        quoteId = required(quoteId, "quoteId");
        if (fromSequence < 1 || toSequence <= fromSequence) {
            throw new IllegalArgumentException("segment must satisfy 1 <= fromSequence < toSequence");
        }
        if (passengerCount < 1) throw new IllegalArgumentException("passengerCount must be positive");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) throw new IllegalArgumentException("amount must be positive");
        if (amount.stripTrailingZeros().scale() > 4 || amount.precision() > 19) {
            throw new IllegalArgumentException("amount exceeds supported precision (19 digits, 4 decimal places)");
        }
        currency = required(currency, "currency");
        if (!currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("currency must be uppercase ISO-4217");
        try {
            Currency.getInstance(currency);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("currency must be a supported ISO-4217 code", exception);
        }
        Objects.requireNonNull(holdExpiresAt, "holdExpiresAt");
        Objects.requireNonNull(status, "status");
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt must not precede createdAt");
        if ((status == TransportCheckoutStatus.HOLD_CREATED
                || status == TransportCheckoutStatus.PAYMENT_PENDING
                || status == TransportCheckoutStatus.PAYMENT_ACTION_REQUIRED
                || status == TransportCheckoutStatus.PAYMENT_SUCCEEDED
                || status == TransportCheckoutStatus.PAYMENT_FAILED
                || status == TransportCheckoutStatus.CANCELLED
                || status == TransportCheckoutStatus.RECONCILIATION_REQUIRED
                || status == TransportCheckoutStatus.REFUND_REQUESTED
                || status == TransportCheckoutStatus.REFUND_PENDING
                || status == TransportCheckoutStatus.REFUNDED
                || status == TransportCheckoutStatus.REFUND_FAILED) && holdId == null) {
            throw new IllegalArgumentException("checkout state requires a hold id");
        }
        if ((status == TransportCheckoutStatus.PAYMENT_PENDING
                || status == TransportCheckoutStatus.PAYMENT_ACTION_REQUIRED
                || status == TransportCheckoutStatus.PAYMENT_SUCCEEDED
                || status == TransportCheckoutStatus.PAYMENT_FAILED
                || status == TransportCheckoutStatus.RECONCILIATION_REQUIRED
                || status == TransportCheckoutStatus.REFUND_PENDING
                || status == TransportCheckoutStatus.REFUNDED
                || status == TransportCheckoutStatus.REFUND_FAILED) && paymentId == null) {
            throw new IllegalArgumentException("payment state requires a payment id");
        }
        if ((status == TransportCheckoutStatus.REFUND_PENDING
                || status == TransportCheckoutStatus.REFUNDED
                || status == TransportCheckoutStatus.REFUND_FAILED)
                && (refundId == null || refundStatus == null)) {
            throw new IllegalArgumentException("refund state requires a refund operation");
        }
        boolean noRefund = refundId == null && refundStatus == null && refundReason == null;
        boolean refundIntent = refundId == null && refundStatus == null
                && status == TransportCheckoutStatus.REFUND_REQUESTED
                && refundReason != null && !refundReason.isBlank();
        boolean hasRefund = refundId != null && refundStatus != null
                && refundReason != null && !refundReason.isBlank();
        if (!noRefund && !refundIntent && !hasRefund) {
            throw new IllegalArgumentException("refund ID, status, and reason must be consistent with refund state");
        }
        if (refundReason != null) refundReason = required(refundReason, "refundReason");
    }

    public TransportCheckout withHold(UUID nextHoldId, Instant now) {
        if (status != TransportCheckoutStatus.REQUESTED && status != TransportCheckoutStatus.HOLD_CREATED) {
            throw new IllegalStateException("cannot attach a hold from state " + status);
        }
        return copy(nextHoldId, paymentId, paymentActionUri, refundId, refundStatus, refundReason,
                TransportCheckoutStatus.HOLD_CREATED, now);
    }

    public TransportCheckout withPayment(String nextPaymentId, String nextPaymentActionUri,
                                          TransportCheckoutStatus nextStatus, Instant now) {
        if (status != TransportCheckoutStatus.HOLD_CREATED
                && status != TransportCheckoutStatus.PAYMENT_PENDING
                && status != TransportCheckoutStatus.PAYMENT_ACTION_REQUIRED) {
            throw new IllegalStateException("cannot attach/update payment from state " + status);
        }
        if (nextStatus != TransportCheckoutStatus.PAYMENT_PENDING
                && nextStatus != TransportCheckoutStatus.PAYMENT_ACTION_REQUIRED
                && nextStatus != TransportCheckoutStatus.PAYMENT_SUCCEEDED
                && nextStatus != TransportCheckoutStatus.PAYMENT_FAILED
                && nextStatus != TransportCheckoutStatus.CANCELLED
                && nextStatus != TransportCheckoutStatus.RECONCILIATION_REQUIRED) {
            throw new IllegalArgumentException("invalid payment checkout state " + nextStatus);
        }
        return copy(holdId, required(nextPaymentId, "paymentId"), nextPaymentActionUri,
                refundId, refundStatus, refundReason, nextStatus, now);
    }

    public TransportCheckout withRefundIntent(String nextRefundReason, Instant now) {
        if (status != TransportCheckoutStatus.RECONCILIATION_REQUIRED) {
            throw new IllegalStateException("refund intent can only start from reconciliation-required checkout");
        }
        String reason = required(nextRefundReason, "refundReason");
        if (reason.length() > 500) throw new IllegalArgumentException("refundReason must not exceed 500 characters");
        return copy(holdId, paymentId, paymentActionUri, null, null, reason,
                TransportCheckoutStatus.REFUND_REQUESTED, now);
    }

    public TransportCheckout withRefund(String nextRefundId, TransportCheckoutRefundStatus nextRefundStatus,
                                        String nextRefundReason, Instant now) {
        if (status != TransportCheckoutStatus.RECONCILIATION_REQUIRED
                && status != TransportCheckoutStatus.REFUND_REQUESTED
                && status != TransportCheckoutStatus.REFUND_PENDING) {
            throw new IllegalStateException("refund compensation is only allowed for reconciliation-required checkouts");
        }
        Objects.requireNonNull(nextRefundStatus, "refundStatus");
        String requiredRefundId = required(nextRefundId, "refundId");
        String requiredRefundReason = required(nextRefundReason, "refundReason");
        if (requiredRefundReason.length() > 500) {
            throw new IllegalArgumentException("refundReason must not exceed 500 characters");
        }
        TransportCheckoutStatus nextStatus = switch (nextRefundStatus) {
            case REQUESTED, PENDING, UNKNOWN -> TransportCheckoutStatus.REFUND_PENDING;
            case SUCCEEDED -> TransportCheckoutStatus.REFUNDED;
            case FAILED -> TransportCheckoutStatus.REFUND_FAILED;
        };
        return copy(holdId, paymentId, paymentActionUri, requiredRefundId, nextRefundStatus,
                requiredRefundReason, nextStatus, now);
    }

    public TransportCheckout withStatus(TransportCheckoutStatus nextStatus, Instant now) {
        Objects.requireNonNull(nextStatus, "nextStatus");
        return copy(holdId, paymentId, paymentActionUri, refundId, refundStatus, refundReason, nextStatus, now);
    }

    private TransportCheckout copy(UUID nextHoldId, String nextPaymentId, String nextPaymentActionUri,
                                   String nextRefundId, TransportCheckoutRefundStatus nextRefundStatus,
                                   String nextRefundReason, TransportCheckoutStatus nextStatus, Instant now) {
        Objects.requireNonNull(now, "now");
        if (now.isBefore(updatedAt)) throw new IllegalArgumentException("update time must not go backwards");
        return new TransportCheckout(tenantId, checkoutId, idempotencyKey, requestFingerprint,
                departureId, quoteId, fromSequence, toSequence, passengerCount, amount, currency,
                holdExpiresAt, nextHoldId, nextPaymentId, nextPaymentActionUri, nextRefundId, nextRefundStatus,
                nextRefundReason, nextStatus, Math.addExact(version, 1),
                createdAt, now);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
