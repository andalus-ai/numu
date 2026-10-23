package tech.kayys.numou.planning.application.port;

import tech.kayys.numou.planning.domain.WorkBreakdownStructure;

import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped persistence boundary for immutable work-breakdown snapshots.
 * Implementations must never resolve a structure outside the supplied tenant.
 */
public interface WorkBreakdownRepository {
    /** Persists a new immutable snapshot; an existing (tenant, structure ID) cannot be overwritten. */
    void create(UUID tenantId, WorkBreakdownStructure structure);

    Optional<WorkBreakdownStructure> findById(UUID tenantId, UUID structureId);
}
