package tech.kayys.numou.warehouse.application;

import java.time.Instant;
import java.util.Optional;

/** Tenant-scoped durable job store with optimistic checkpoint updates. */
public interface WarehouseRecoveryAuditExportJobRepository {
    void insert(WarehouseRecoveryAuditExportJob job);
    Optional<WarehouseRecoveryAuditExportJob> find(String tenantId, String exportId);
    boolean update(WarehouseRecoveryAuditExportJob job, long expectedVersion);
    Optional<WarehouseRecoveryAuditExportJob> claimNext(String workerId, Instant now, Instant leaseUntil);
}
