package tech.kayys.numou.scope.spi;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.scope.domain.BreakdownStructureId;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Serializes WBS mutations for one tenant and structure across application
 * instances. This lock is a concurrency primitive, never an authorization
 * check; repository queries must remain tenant-qualified.
 */
public interface TenantBreakdownStructureWriteLock {
    <T> CompletionStage<T> withLock(
            TenantId tenantId,
            BreakdownStructureId structureId,
            Supplier<CompletionStage<T>> operation);
}
