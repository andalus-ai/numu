package tech.kayys.numou.affiliate.application;

import tech.kayys.numou.affiliate.domain.CommissionEntry;
import tech.kayys.numou.affiliate.domain.CommissionEntryType;
import tech.kayys.numou.affiliate.domain.CommissionFinancePosting;
import tech.kayys.numou.affiliate.domain.CommissionFinancePostingStatus;
import tech.kayys.numou.affiliate.domain.CommissionStatus;
import tech.kayys.numou.affiliate.spi.CommissionEntryRepository;
import tech.kayys.numou.affiliate.spi.CommissionFinanceOutboxRepository;
import tech.kayys.numou.affiliate.spi.CommissionFinancePort;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Requests and dispatches Finance liability postings. The outbox is the retry boundary;
 * Finance must independently deduplicate by the stable idempotency key.
 */
public final class DefaultCommissionFinanceService implements CommissionFinanceApplicationPort {
    private static final Duration DEFAULT_LEASE = Duration.ofSeconds(60);

    private final CommissionEntryRepository entries;
    private final CommissionFinanceOutboxRepository outbox;
    private final CommissionFinancePort finance;

    public DefaultCommissionFinanceService(
            CommissionEntryRepository entries,
            CommissionFinanceOutboxRepository outbox,
            CommissionFinancePort finance) {
        this.entries = Objects.requireNonNull(entries, "entries is required");
        this.outbox = Objects.requireNonNull(outbox, "outbox is required");
        this.finance = Objects.requireNonNull(finance, "finance is required");
    }

    @Override
    public CompletionStage<CommissionFinancePosting> requestLiabilityPosting(
            AffiliateCommandContext context, UUID commissionEntryId) {
        Objects.requireNonNull(context, "context is required");
        Objects.requireNonNull(commissionEntryId, "commissionEntryId is required");
        return entries.findById(context.tenantId(), commissionEntryId).thenCompose(optional -> {
            if (optional.isEmpty()) return failed(new IllegalArgumentException("Commission entry not found for tenant"));
            CommissionEntry entry = optional.get();
            if (!entry.tenantId().equals(context.tenantId())) {
                return failed(new IllegalStateException("Commission repository returned a cross-tenant entry"));
            }
            if (entry.status() != CommissionStatus.APPROVED) {
                return failed(new IllegalStateException("Only approved commission entries can be posted to Finance"));
            }
            if (entry.type() != CommissionEntryType.EARNING && entry.type() != CommissionEntryType.REVERSAL) {
                return failed(new IllegalStateException("Only earnings and reversals can be posted to Finance"));
            }
            if (entry.type() == CommissionEntryType.EARNING && entry.commissionAmount().signum() <= 0
                    || entry.type() == CommissionEntryType.REVERSAL && entry.commissionAmount().signum() >= 0) {
                return failed(new IllegalStateException("Commission entry sign does not match its type"));
            }
            Instant now = Instant.now();
            String key = "affiliate-finance:" + entry.tenantId() + ":" + entry.id();
            CommissionFinancePosting proposed = new CommissionFinancePosting(
                    UUID.randomUUID(), entry.tenantId(), entry.id(), entry.programId(), entry.partnerId(),
                    entry.sourceType(), entry.sourceId(), entry.conversionEventId(), entry.currency(),
                    entry.commissionAmount(), entry.type(), key, context.correlationId(),
                    CommissionFinancePostingStatus.PENDING, 0, null, null, now, now);
            return outbox.enqueueOrGet(proposed);
        });
    }

    @Override
    public CompletionStage<Integer> dispatchPending(
            AffiliateCommandContext context, String workerId, int limit) {
        Objects.requireNonNull(context, "context is required");
        String worker = required(workerId, "workerId");
        if (limit < 1 || limit > 500) return failed(new IllegalArgumentException("limit must be between 1 and 500"));
        return outbox.claimPending(worker, Instant.now(), DEFAULT_LEASE, limit)
                .thenCompose(claimed -> dispatchSequentially(context.tenantId(), claimed, 0, 0));
    }

    private CompletionStage<Integer> dispatchSequentially(
            String tenantId, List<CommissionFinanceOutboxRepository.ClaimedPosting> claimed,
            int index, int completed) {
        if (index >= claimed.size()) return CompletableFuture.completedFuture(completed);
        CommissionFinanceOutboxRepository.ClaimedPosting item = claimed.get(index);
        CommissionFinancePosting posting = item.posting();
        if (!posting.tenantId().equals(tenantId)) {
            return outbox.requireReconciliation(tenantId, posting.id(), item.leaseToken(),
                    "Claimed posting belongs to a different tenant", Instant.now())
                    .thenCompose(ignored -> dispatchSequentially(tenantId, claimed, index + 1, completed));
        }
        return finance.postCommissionLiability(posting)
                .thenCompose(receipt -> {
                    if (!matches(posting, receipt)) {
                        return outbox.requireReconciliation(tenantId, posting.id(), item.leaseToken(),
                                "Finance receipt does not match tenant, idempotency key, currency, or amount",
                                Instant.now()).thenCompose(ignored ->
                                dispatchSequentially(tenantId, claimed, index + 1, completed));
                    }
                    return outbox.complete(tenantId, posting.id(), item.leaseToken(),
                            receipt.externalReference(), Instant.now())
                            .thenCompose(ignored -> dispatchSequentially(tenantId, claimed, index + 1, completed + 1));
                })
                .exceptionallyCompose(error -> outbox.recordRetryableFailure(
                        tenantId, posting.id(), item.leaseToken(), safeMessage(error), Instant.now())
                        .thenCompose(ignored -> dispatchSequentially(tenantId, claimed, index + 1, completed)));
    }

    @Override
    public CompletionStage<Boolean> reconcile(AffiliateCommandContext context, UUID postingId) {
        Objects.requireNonNull(context, "context is required");
        Objects.requireNonNull(postingId, "postingId is required");
        return outbox.findById(context.tenantId(), postingId).thenCompose(optional -> {
            if (optional.isEmpty()) return failed(new IllegalArgumentException("Finance posting request not found for tenant"));
            CommissionFinancePosting posting = optional.get();
            if (!posting.tenantId().equals(context.tenantId())) {
                return failed(new IllegalStateException("Outbox returned a cross-tenant posting"));
            }
            if (posting.status() == CommissionFinancePostingStatus.POSTED) {
                return CompletableFuture.completedFuture(true);
            }
            return finance.findPosting(context.tenantId(), posting.idempotencyKey()).thenCompose(receipt -> {
                if (receipt.isEmpty()) return CompletableFuture.completedFuture(false);
                if (!matches(posting, receipt.get())) {
                    return failed(new IllegalStateException("Existing Finance posting does not match affiliate request"));
                }
                return outbox.reconcilePosted(context.tenantId(), posting.id(),
                        receipt.get().externalReference(), Instant.now()).thenApply(updated ->
                        updated.status() == CommissionFinancePostingStatus.POSTED);
            });
        });
    }

    private static boolean matches(CommissionFinancePosting posting, CommissionFinancePort.FinancePostingReceipt receipt) {
        return receipt != null
                && posting.tenantId().equals(receipt.tenantId())
                && posting.idempotencyKey().equals(receipt.idempotencyKey())
                && posting.currency().equalsIgnoreCase(receipt.currency())
                && posting.amount().compareTo(receipt.amount()) == 0;
    }

    private static String safeMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) root = root.getCause();
        String message = root.getMessage();
        if (message == null || message.isBlank()) message = root.getClass().getSimpleName();
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private static <T> CompletionStage<T> failed(Throwable error) {
        return CompletableFuture.failedFuture(error);
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        String normalized = value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
