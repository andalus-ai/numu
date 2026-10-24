package tech.kayys.numou.warehouse.application;

/** Append-only access-audit port; deliberately exposes no update or delete operations. */
public interface WarehouseRecoveryAuditExportAccessRepository {
    void append(WarehouseRecoveryAuditExportAccessEvent event);
}
