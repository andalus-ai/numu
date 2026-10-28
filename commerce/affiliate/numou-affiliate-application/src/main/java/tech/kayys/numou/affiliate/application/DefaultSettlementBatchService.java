package tech.kayys.numou.affiliate.application;

import tech.kayys.numou.affiliate.domain.PartnerPayout;
import tech.kayys.numou.affiliate.domain.PartnerPayoutStatus;
import tech.kayys.numou.affiliate.domain.SettlementBatch;
import tech.kayys.numou.affiliate.domain.SettlementBatchItem;
import tech.kayys.numou.affiliate.domain.SettlementBatchStatus;
import tech.kayys.numou.affiliate.spi.PartnerPayoutRepository;
import tech.kayys.numou.affiliate.spi.SettlementBatchRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Settlement batching and maker-checker orchestration; does not move money. */
public final class DefaultSettlementBatchService implements SettlementBatchApplicationPort {
    private final PartnerPayoutRepository payouts;
    private final SettlementBatchRepository batches;

    public DefaultSettlementBatchService(PartnerPayoutRepository payouts, SettlementBatchRepository batches) {
        this.payouts = Objects.requireNonNull(payouts, "payouts is required");
        this.batches = Objects.requireNonNull(batches, "batches is required");
    }

    @Override
    public CompletionStage<SettlementBatch> createBatch(
            AffiliateCommandContext context, List<UUID> payoutIds, String idempotencyKey) {
        Objects.requireNonNull(context, "context is required");
        Objects.requireNonNull(payoutIds, "payoutIds is required");
        if (payoutIds.isEmpty()) return failed(new IllegalArgumentException("at least one payout is required"));
        if (new HashSet<>(payoutIds).size() != payoutIds.size()) {
            return failed(new IllegalArgumentException("duplicate payout IDs are not allowed"));
        }
        String key = required(idempotencyKey, "idempotencyKey");
        List<CompletableFuture<PartnerPayout>> futures = new ArrayList<>();
        for (UUID payoutId : payoutIds) {
            if (payoutId == null) return failed(new IllegalArgumentException("payout ID cannot be null"));
            futures.add(payouts.findById(context.tenantId(), payoutId).thenCompose(found -> {
                if (found.isEmpty()) return failed(new IllegalArgumentException("Payout not found for tenant"));
                PartnerPayout payout = found.get();
                if (!context.tenantId().equals(payout.tenantId())) {
                    return failed(new IllegalStateException("Payout repository returned cross-tenant data"));
                }
                if (payout.status() != PartnerPayoutStatus.REQUESTED) {
                    return failed(new IllegalStateException("Only requested payouts can be batched"));
                }
                return CompletableFuture.completedFuture(payout);
            }).toCompletableFuture());
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenCompose(ignored -> {
            List<PartnerPayout> selected = futures.stream().map(CompletableFuture::join).toList();
            String currency = selected.getFirst().currency().toUpperCase(Locale.ROOT);
            List<SettlementBatchItem> items = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            for (PartnerPayout payout : selected) {
                if (!currency.equalsIgnoreCase(payout.currency())) {
                    return failed(new IllegalArgumentException("Settlement batches cannot mix currencies"));
                }
                items.add(new SettlementBatchItem(payout.id(), payout.partnerId(), payout.amount(), payout.currency()));
                total = total.add(payout.amount());
            }
            Instant now = Instant.now();
            SettlementBatch batch = new SettlementBatch(UUID.randomUUID(), context.tenantId(), currency,
                    items, total, key, SettlementBatchStatus.READY_FOR_APPROVAL, context.actorId(), null, now, now, 0);
            return batches.saveOrGet(batch);
        });
    }

    @Override
    public CompletionStage<SettlementBatch> approveBatch(AffiliateCommandContext context, UUID batchId) {
        Objects.requireNonNull(context, "context is required");
        return find(context, batchId).thenCompose(batch -> {
            SettlementBatch approved = batch.approve(context.actorId(), Instant.now());
            return batches.update(context.tenantId(), batch.id(), batch.version(), approved);
        });
    }

    @Override
    public CompletionStage<SettlementBatch> beginSubmission(AffiliateCommandContext context, UUID batchId) {
        Objects.requireNonNull(context, "context is required");
        return find(context, batchId).thenCompose(batch -> {
            SettlementBatch submitting = batch.beginSubmission(Instant.now());
            return batches.update(context.tenantId(), batch.id(), batch.version(), submitting);
        });
    }

    private CompletionStage<SettlementBatch> find(AffiliateCommandContext context, UUID batchId) {
        Objects.requireNonNull(batchId, "batchId is required");
        return batches.findById(context.tenantId(), batchId).thenCompose(found -> {
            if (found.isEmpty()) return failed(new IllegalArgumentException("Settlement batch not found for tenant"));
            if (!context.tenantId().equals(found.get().tenantId())) {
                return failed(new IllegalStateException("Batch repository returned cross-tenant data"));
            }
            return CompletableFuture.completedFuture(found.get());
        });
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        String normalized = value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }

    private static <T> CompletionStage<T> failed(Throwable error) {
        return CompletableFuture.failedFuture(error);
    }
}
