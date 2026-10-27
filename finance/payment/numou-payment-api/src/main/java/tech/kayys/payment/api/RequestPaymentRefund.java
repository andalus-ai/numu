package tech.kayys.payment.api;

import java.math.BigDecimal;
import java.util.Objects;

/** Tenant-scoped, idempotent request for a full or partial refund. */
public record RequestPaymentRefund(
        String tenantId,
        String paymentId,
        String idempotencyKey,
        BigDecimal amount,
        String reason) {

    public RequestPaymentRefund {
        tenantId = required(tenantId, "tenantId");
        paymentId = required(paymentId, "paymentId");
        idempotencyKey = required(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("refund amount must be positive");
        }
        reason = required(reason, "reason");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}