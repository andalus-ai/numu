package tech.kayys.numou.affiliate.spi;

import tech.kayys.numou.affiliate.domain.SettlementBatch;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Durable aggregate repository. saveOrGet must atomically deduplicate tenant + idempotency key
 * and enforce that one payout cannot belong to multiple active batches.
 * update must compare-and-set the expected version.
 */
public interface SettlementBatchRepository {
    CompletionStage<SettlementBatch> saveOrGet(SettlementBatch proposed);

    CompletionStage<Optional<SettlementBatch>> findById(String tenantId, UUID batchId);

    CompletionStage<SettlementBatch> update(String tenantId, UUID batchId, long expectedVersion,
                                             SettlementBatch replacement);
}
