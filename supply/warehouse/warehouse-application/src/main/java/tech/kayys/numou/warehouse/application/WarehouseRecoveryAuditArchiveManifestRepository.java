package tech.kayys.numou.warehouse.application;

import java.util.Optional;

/** Durable storage port for immutable archive manifests. Implementations must enforce tenant-scoped access. */
public interface WarehouseRecoveryAuditArchiveManifestRepository {
    void insert(WarehouseRecoveryAuditArchiveManifest manifest);
    Optional<WarehouseRecoveryAuditArchiveManifest> find(String tenantId, String exportId);
}
