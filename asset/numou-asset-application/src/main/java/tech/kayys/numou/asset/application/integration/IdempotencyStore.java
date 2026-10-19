package tech.kayys.numou.asset.application.integration;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Tenant-scoped store for client-supplied idempotency keys (ASSET-28 §acceptance).
 *
 * <p>Separate from the domain so a repeated external {@code POST} can be made
 * safe without leaking transport concerns into the aggregate. Recording a key
 * must be atomic and unique per {@code (tenant, key)}.</p>
 */
public interface IdempotencyStore {

    CompletionStage<Optional<IdempotencyRecord>> find(String tenantId, String key);

    /** Returns {@code true} when the key was newly recorded, {@code false} if it already existed. */
    CompletionStage<Boolean> record(IdempotencyRecord record);

    default CompletionStage<Boolean> isReplay(String tenantId, String key) {
        return find(tenantId, key).thenApply(Optional::isPresent);
    }
}