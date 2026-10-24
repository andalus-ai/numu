package tech.kayys.numou.courier.application.port;

import tech.kayys.numou.courier.domain.HubScanCommand;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Persistence boundary for hub operations.
 *
 * Implementations must atomically persist the accepted scan receipt, any derived
 * append-only tracking event(s), and corresponding outbox row(s). The idempotency
 * ledger must be checked before changing operational state. Implementations must
 * scope every read/write by command.tenantId() and reject a key reused with a
 * different payload fingerprint without changing the original receipt.
 */
public interface HubOperationsStore {

    CompletionStage<ScanReceiptResult> ingest(HubScanCommand command);

    enum Outcome {
        ACCEPTED,
        REPLAYED,
        IDEMPOTENCY_CONFLICT
    }

    record ScanReceiptResult(Outcome outcome, String receiptReference) {
        public ScanReceiptResult {
            Objects.requireNonNull(outcome, "outcome must not be null");
            if (receiptReference == null || receiptReference.isBlank()) {
                throw new IllegalArgumentException("receiptReference must not be blank");
            }
            receiptReference = receiptReference.trim();
        }
    }
}
