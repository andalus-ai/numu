package tech.kayys.numou.affiliate.spi;

import tech.kayys.numou.affiliate.domain.CommissionEntry;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Durable idempotency boundary. Implementations must atomically enforce uniqueness on
 * tenantId + idempotencyKey and reject a conflicting payload for a reused key.
 */
public interface CommissionEntryRepository {
    CompletionStage<CommissionEntry> saveOrGet(CommissionEntry proposed);
    CompletionStage<Optional<CommissionEntry>> findById(String tenantId, UUID entryId);
}
