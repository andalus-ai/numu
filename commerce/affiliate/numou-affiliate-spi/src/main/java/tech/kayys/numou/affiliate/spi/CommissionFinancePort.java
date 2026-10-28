package tech.kayys.numou.affiliate.spi;

import tech.kayys.numou.affiliate.domain.CommissionFinancePosting;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Adapter contract to Finance. Implementations map semantic commission liability instructions to
 * configured chart-of-account IDs and must atomically deduplicate by tenant + idempotency key.
 * No affiliate module may directly mutate Finance accounts or journal tables.
 */
public interface CommissionFinancePort {
    CompletionStage<FinancePostingReceipt> postCommissionLiability(CommissionFinancePosting posting);

    /** Used after timeouts or ambiguous failures before retrying a possibly committed posting. */
    CompletionStage<Optional<FinancePostingReceipt>> findPosting(String tenantId, String idempotencyKey);

    record FinancePostingReceipt(
            String tenantId,
            String idempotencyKey,
            String externalReference,
            String currency,
            java.math.BigDecimal amount,
            Instant postedAt) {
        public FinancePostingReceipt {
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
            if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey is required");
            if (externalReference == null || externalReference.isBlank()) throw new IllegalArgumentException("externalReference is required");
            if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency is required");
            if (amount == null || amount.signum() == 0) throw new IllegalArgumentException("amount must be non-zero");
            if (postedAt == null) throw new IllegalArgumentException("postedAt is required");
        }
    }
