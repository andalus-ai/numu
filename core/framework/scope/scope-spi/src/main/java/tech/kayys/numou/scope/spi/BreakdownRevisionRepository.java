package tech.kayys.numou.scope.spi;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.scope.domain.BreakdownRevision;
import tech.kayys.numou.scope.domain.BreakdownRevisionId;
import tech.kayys.numou.scope.domain.BreakdownStructureId;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Tenant-explicit persistence contract for immutable WBS revisions.
 *
 * <p>Implementations must scope every query by tenant and structure, enforce
 * immutable revision content, and atomically compare-and-set the current
 * revision pointer. A missing or mismatched tenant must not disclose whether
 * the requested identifier exists for another tenant.</p>
 */
public interface BreakdownRevisionRepository {

    CompletionStage<Optional<BreakdownRevision>> findCurrent(
            TenantId tenantId,
            BreakdownStructureId structureId);

    CompletionStage<Optional<BreakdownRevision>> findById(
            TenantId tenantId,
            BreakdownStructureId structureId,
            BreakdownRevisionId revisionId);

    /**
     * Persists a new immutable revision and advances the structure's current
     * revision only when the current revision equals {@code expectedCurrent}.
     * Use {@code null} for expectedCurrent only when creating the first revision.
     *
     * <p>The implementation must perform inserts and head-pointer update in
     * one transaction. A stale expected revision must fail without publishing
     * a partial revision.</p>
     */
    CompletionStage<BreakdownRevision> append(
            BreakdownRevision revision,
            BreakdownRevisionId expectedCurrent);
}
