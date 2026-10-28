package tech.kayys.numou.purchasing.spi;

import tech.kayys.numou.purchasing.domain.procurement.PurchaseRequisition;

import java.util.Optional;

/**
 * Purchasing-owned persistence port for requisitions.
 *
 * <p>Every lookup is tenant-scoped. Implementations must persist the aggregate and its
 * state transition atomically, and must enforce uniqueness of requisition IDs within a
 * tenant. Callers must not use an identifier as proof of authorization.</p>
 */
public interface PurchaseRequisitionRepository {

    Optional<PurchaseRequisition> findByTenantAndId(String tenantId, String requisitionId);

    void save(PurchaseRequisition requisition);
}
