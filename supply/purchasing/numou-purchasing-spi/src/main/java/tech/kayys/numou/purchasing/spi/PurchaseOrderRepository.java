package tech.kayys.numou.purchasing.spi;

import tech.kayys.numou.purchasing.domain.procurement.PurchaseOrder;

import java.util.Optional;

/**
 * Purchasing-owned persistence port for purchase orders.
 *
 * <p>Implementations must enforce tenant scope and save the order's status and received
 * quantities atomically. A production adapter must use optimistic versioning, locking,
 * or equivalent concurrency control so concurrent receipts cannot overwrite one another.</p>
 */
public interface PurchaseOrderRepository {

    Optional<PurchaseOrder> findByTenantAndId(String tenantId, String purchaseOrderId);

    void save(PurchaseOrder purchaseOrder);
}
