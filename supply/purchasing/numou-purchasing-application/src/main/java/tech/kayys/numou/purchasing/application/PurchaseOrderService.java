package tech.kayys.numou.purchasing.application;

import tech.kayys.numou.purchasing.domain.procurement.GoodsReceipt;
import tech.kayys.numou.purchasing.domain.procurement.PoLine;
import tech.kayys.numou.purchasing.domain.procurement.PurchaseOrder;
import tech.kayys.numou.purchasing.spi.GoodsReceiptRepository;
import tech.kayys.numou.purchasing.spi.PurchaseOrderRepository;
import tech.kayys.numou.purchasing.spi.PurchasingUnitOfWork;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Application use cases for purchase orders and goods receipts.
 *
 * <p>The caller supplies a stable receipt ID. Replaying the same receipt is idempotent;
 * reusing that ID with different receipt data is rejected. The unit-of-work adapter must
 * make the purchase-order quantity transition and receipt record atomic.</p>
 */
public final class PurchaseOrderService {
    private final PurchaseOrderRepository orders;
    private final GoodsReceiptRepository receipts;
    private final PurchasingUnitOfWork unitOfWork;

    public PurchaseOrderService(PurchaseOrderRepository orders, GoodsReceiptRepository receipts,
                                PurchasingUnitOfWork unitOfWork) {
        this.orders = Objects.requireNonNull(orders, "orders is required");
        this.receipts = Objects.requireNonNull(receipts, "receipts is required");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork is required");
    }

    public PurchaseOrder createDraft(String tenantId, String purchaseOrderId, String ledgerId, String vendorId) {
        require(tenantId, "tenantId");
        require(purchaseOrderId, "purchaseOrderId");
        require(ledgerId, "ledgerId");
        require(vendorId, "vendorId");
        if (orders.findByTenantAndId(tenantId, purchaseOrderId).isPresent()) {
            throw new IllegalStateException("Purchase order already exists for tenant and id");
        }
        PurchaseOrder order = PurchaseOrder.create(purchaseOrderId, tenantId, ledgerId, vendorId);
        orders.save(order);
        return order;
    }

    public PurchaseOrder addLine(String tenantId, String purchaseOrderId, PoLine line) {
        PurchaseOrder order = findOrder(tenantId, purchaseOrderId);
        order.addLine(Objects.requireNonNull(line, "line is required"));
        orders.save(order);
        return order;
    }

    public PurchaseOrder approve(String tenantId, String purchaseOrderId, String actorId) {
        require(actorId, "actorId");
        PurchaseOrder order = findOrder(tenantId, purchaseOrderId);
        order.approve(actorId);
        orders.save(order);
        return order;
    }

    public PurchaseOrder sendToVendor(String tenantId, String purchaseOrderId) {
        PurchaseOrder order = findOrder(tenantId, purchaseOrderId);
        order.sendToVendor();
        orders.save(order);
        return order;
    }

    public GoodsReceipt receive(String tenantId, String receiptId, String ledgerId, String purchaseOrderId,
                                String purchaseOrderLineId, BigDecimal quantity, String actorId) {
        require(tenantId, "tenantId");
        require(receiptId, "receiptId");
        require(ledgerId, "ledgerId");
        require(purchaseOrderId, "purchaseOrderId");
        require(purchaseOrderLineId, "purchaseOrderLineId");
        require(actorId, "actorId");
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }

        var existing = receipts.findByTenantAndId(tenantId, receiptId);
        if (existing.isPresent()) {
            GoodsReceipt receipt = existing.get();
            if (!receipt.poId().equals(purchaseOrderId) || !receipt.poLineId().equals(purchaseOrderLineId)
                    || receipt.quantityReceived().compareTo(quantity) != 0
                    || !receipt.ledgerId().equals(ledgerId) || !receipt.receivedBy().equals(actorId)) {
                throw new IllegalArgumentException("Receipt ID was already used with a different payload");
            }
            return receipt;
        }

        AtomicReference<GoodsReceipt> result = new AtomicReference<>();
        unitOfWork.required(() -> {
            // Recheck inside the transaction to close the ordinary retry race; adapters must
            // also enforce a unique (tenant_id, receipt_id) constraint for concurrent requests.
            var duplicate = receipts.findByTenantAndId(tenantId, receiptId);
            if (duplicate.isPresent()) {
                GoodsReceipt receipt = duplicate.get();
                if (!receipt.poId().equals(purchaseOrderId) || !receipt.poLineId().equals(purchaseOrderLineId)
                        || receipt.quantityReceived().compareTo(quantity) != 0
                        || !receipt.ledgerId().equals(ledgerId) || !receipt.receivedBy().equals(actorId)) {
                    throw new IllegalArgumentException("Receipt ID was already used with a different payload");
                }
                result.set(receipt);
                return;
            }
            PurchaseOrder order = findOrder(tenantId, purchaseOrderId);
            if (!order.ledgerId().equals(ledgerId)) {
                throw new IllegalArgumentException("Receipt ledger does not match purchase order ledger");
            }
            order.recordReceipt(purchaseOrderLineId, quantity);
            GoodsReceipt receipt = new GoodsReceipt(receiptId, tenantId, ledgerId, purchaseOrderId,
                    purchaseOrderLineId, quantity, actorId);
            orders.save(order);
            receipts.save(receipt);
            result.set(receipt);
        });
        return Objects.requireNonNull(result.get(), "Unit of work completed without a receipt result");
    }

    public PurchaseOrder findOrder(String tenantId, String purchaseOrderId) {
        require(tenantId, "tenantId");
        require(purchaseOrderId, "purchaseOrderId");
        return orders.findByTenantAndId(tenantId, purchaseOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found for tenant"));
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}