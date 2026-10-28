package tech.kayys.numou.affiliate.spi;

import tech.kayys.numou.affiliate.domain.PartnerPayout;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Adapter into Wallet/Payment public application contracts. It must not mutate their persistence
 * directly, must deduplicate by tenant + idempotency key, and must support authoritative lookup
 * after an ambiguous timeout.
 */
public interface PartnerPayoutPort {
    CompletionStage<PayoutReceipt> submit(PartnerPayout payout);

    CompletionStage<Optional<PayoutReceipt>> findByIdempotencyKey(String tenantId, String idempotencyKey);

    record PayoutReceipt(
            String tenantId,
            String idempotencyKey,
            String providerReference,
            String currency,
            java.math.BigDecimal amount,
            Outcome outcome,
            Instant occurredAt) {
        public PayoutReceipt {
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
            if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey is required");
            if (providerReference == null || providerReference.isBlank()) throw new IllegalArgumentException("providerReference is required");
            if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency is required");
            if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("amount must be positive");
            if (outcome == null) throw new IllegalArgumentException("outcome is required");
            if (occurredAt == null) throw new IllegalArgumentException("occurredAt is required");
        }
    }

    enum Outcome {
        ACCEPTED,
        CONFIRMED,
        RETURNED,
        PENDING_RECONCILIATION
    }
}
