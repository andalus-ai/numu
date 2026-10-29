package tech.kayys.numou.automotiveparts.purchasing;

import tech.kayys.numou.purchasing.domain.procurement.GoodsReceipt;

import java.time.Instant;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Bounded reconciliation pass for durable PENDING receipt postings.
 * A scheduler/worker may invoke this periodically; this class intentionally does not
 * create its own thread or impose a deployment-specific scheduling framework.
 */
public final class ReceiptPostingReconciliationService {
    private final ReceiptPostingLedger ledger;
    private final GoodsReceiptInventoryPostingService postingService;
    private final GoodsReceiptLookup receiptLookup;
    private final String workerId;
    private final Duration leaseDuration;

    public ReceiptPostingReconciliationService(ReceiptPostingLedger ledger,
                                               GoodsReceiptInventoryPostingService postingService,
                                               GoodsReceiptLookup receiptLookup) {
        this(ledger, postingService, receiptLookup, "receipt-reconciler-" + java.util.UUID.randomUUID(),
                Duration.ofMinutes(2));
    }

    public ReceiptPostingReconciliationService(ReceiptPostingLedger ledger,
                                               GoodsReceiptInventoryPostingService postingService,
                                               GoodsReceiptLookup receiptLookup,
                                               String workerId, Duration leaseDuration) {
        this.ledger = Objects.requireNonNull(ledger, "ledger is required");
        this.postingService = Objects.requireNonNull(postingService, "postingService is required");
        this.receiptLookup = Objects.requireNonNull(receiptLookup, "receiptLookup is required");
        if (workerId == null || workerId.isBlank()) throw new IllegalArgumentException("workerId is required");
        this.workerId = workerId;
        this.leaseDuration = Objects.requireNonNull(leaseDuration, "leaseDuration is required");
        if (leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
    }

    /**
     * Retries only records older than {@code updatedBefore}; callers should use a conservative
     * age threshold so a live request is not needlessly raced by the worker.
     */
    public ReconciliationReport reconcile(Instant updatedBefore, int limit) {
        Objects.requireNonNull(updatedBefore, "updatedBefore is required");
        var pending = ledger.claimPending(updatedBefore, workerId, Instant.now().plus(leaseDuration), limit);
        int posted = 0, failed = 0, missing = 0;
        for (ReceiptPostingLedger.PostingRecord record : pending) {
            Optional<GoodsReceipt> receipt;
            try {
                receipt = receiptLookup.find(record.tenantId(), record.receiptId());
            } catch (RuntimeException lookupFailure) {
                ledger.markRetryable(record.tenantId(), record.receiptId(), record.payloadFingerprint(),
                        "Receipt lookup failed: " + lookupFailure.getMessage());
                failed++;
                continue;
            }
            if (receipt == null || receipt.isEmpty()) {
                ledger.markRetryable(record.tenantId(), record.receiptId(), record.payloadFingerprint(),
                        "Accepted goods receipt was not found by the configured receipt lookup");
                missing++;
                continue;
            }
            try {
                GoodsReceipt found = receipt.get();
                if (!record.tenantId().equals(found.tenantId())
                        || !record.receiptId().equals(found.receiptId())) {
                    ledger.markRetryable(record.tenantId(), record.receiptId(), record.payloadFingerprint(),
                            "Receipt lookup returned a different tenant or receipt identity");
                    failed++;
                    continue;
                }
                postingService.post(found);
                posted++;
            } catch (RuntimeException postingFailure) {
                failed++;
            }
        }
        return new ReconciliationReport(pending.size(), posted, failed, missing);
    }

    @FunctionalInterface
    public interface GoodsReceiptLookup {
        Optional<GoodsReceipt> find(String tenantId, String receiptId);
    }

    public record ReconciliationReport(int examined, int posted, int failed, int missingReceipts) {}
}
