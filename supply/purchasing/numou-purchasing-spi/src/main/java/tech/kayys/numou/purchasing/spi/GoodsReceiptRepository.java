package tech.kayys.numou.purchasing.spi;

import tech.kayys.numou.purchasing.domain.procurement.GoodsReceipt;

import java.util.Optional;

/**
 * Purchasing-owned persistence port for receipt records.
 *
 * <p>Receipt identity must be unique within a tenant. Production implementations should
 * persist a receipt and the corresponding purchase-order received-quantity transition in
 * one transaction, or use a durable inbox/outbox workflow with recoverable processing.</p>
 */
public interface GoodsReceiptRepository {

    Optional<GoodsReceipt> findByTenantAndId(String tenantId, String receiptId);

    void save(GoodsReceipt receipt);
}
