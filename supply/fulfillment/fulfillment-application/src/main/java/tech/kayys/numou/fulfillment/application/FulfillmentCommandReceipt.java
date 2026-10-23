package tech.kayys.numou.fulfillment.application;

import tech.kayys.numou.fulfillment.domain.FulfillmentOrder;

import java.time.Instant;
import java.util.Objects;

/** Durable idempotency receipt. The result snapshot lets a retry return its original outcome. */
public record FulfillmentCommandReceipt(
        String tenantId,
        FulfillmentCommandKey commandKey,
        String requestFingerprint,
        FulfillmentOrder result,
        Instant recordedAt
) {
    public FulfillmentCommandReceipt {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(commandKey, "commandKey is required");
        Objects.requireNonNull(requestFingerprint, "requestFingerprint is required");
        Objects.requireNonNull(result, "result is required");
        Objects.requireNonNull(recordedAt, "recordedAt is required");
        if (!tenantId.equals(result.tenantId())) throw new IllegalArgumentException("Receipt and result tenant must match");
        if (requestFingerprint.length() != 64) throw new IllegalArgumentException("Fingerprint must be SHA-256 hex");
    }
}
