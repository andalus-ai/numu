package tech.kayys.numou.affiliate.application;

import tech.kayys.numou.affiliate.domain.SettlementBatch;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface SettlementBatchApplicationPort {
    CompletionStage<SettlementBatch> createBatch(
            AffiliateCommandContext context, List<UUID> payoutIds, String idempotencyKey);

    CompletionStage<SettlementBatch> approveBatch(
            AffiliateCommandContext context, UUID batchId);

    CompletionStage<SettlementBatch> beginSubmission(
            AffiliateCommandContext context, UUID batchId);
}
