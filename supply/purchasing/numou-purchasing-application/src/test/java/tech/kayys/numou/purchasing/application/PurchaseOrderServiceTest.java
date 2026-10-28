package tech.kayys.numou.purchasing.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.purchasing.domain.procurement.GoodsReceipt;
import tech.kayys.numou.purchasing.domain.procurement.PoLine;
import tech.kayys.numou.purchasing.domain.procurement.PurchaseOrder;
import tech.kayys.numou.purchasing.spi.GoodsReceiptRepository;
import tech.kayys.numou.purchasing.spi.PurchaseOrderRepository;
import tech.kayys.numou.purchasing.spi.PurchasingUnitOfWork;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PurchaseOrderServiceTest {
    private final Map<String, PurchaseOrder> orders = new HashMap<>();
    private final Map<String, GoodsReceipt> receipts = new HashMap<>();
    private PurchaseOrderService service;

    @BeforeEach
    void setUp() {
        PurchaseOrderRepository orderRepository = new PurchaseOrderRepository() {
            @Override public Optional<PurchaseOrder> findByTenantAndId(String tenantId, String id) {
                PurchaseOrder value = orders.get(tenantId + ":" + id);
                return Optional.ofNullable(value);
            }
            @Override public void save(PurchaseOrder order) {
                orders.put(order.tenantId() + ":" + order.poId(), order);
            }
        };
        GoodsReceiptRepository receiptRepository = new GoodsReceiptRepository() {
            @Override public Optional<GoodsReceipt> findByTenantAndId(String tenantId, String id) {
                return Optional.ofNullable(receipts.get(tenantId + ":" + id));
            }
            @Override public void save(GoodsReceipt receipt) {
                String key = receipt.tenantId() + ":" + receipt.receiptId();
                if (receipts.putIfAbsent(key, receipt) != null) {
                    throw new IllegalStateException("Duplicate receipt");
                }
            }
        };
        PurchasingUnitOfWork unitOfWork = Runnable::run;
        service = new PurchaseOrderService(orderRepository, receiptRepository, unitOfWork);

        service.createDraft("tenant-a", "PO-1", "ledger-a", "supplier-a");
        service.addLine("tenant-a", "PO-1", PoLine.of("LINE-1", "FILTER-01", "Oil filter",
                new BigDecimal("5"), Money.of(new BigDecimal("25000"), "IDR")));
        service.approve("tenant-a", "PO-1", "buyer-1");
        service.sendToVendor("tenant-a", "PO-1");
    }

    @Test
    void receiptIsIdempotentAndPartialReceiptKeepsRemainingQuantity() {
        GoodsReceipt first = service.receive("tenant-a", "GR-1", "ledger-a", "PO-1",
                "LINE-1", new BigDecimal("2"), "receiver-1");
        GoodsReceipt replay = service.receive("tenant-a", "GR-1", "ledger-a", "PO-1",
                "LINE-1", new BigDecimal("2.0"), "receiver-1");

        assertEquals(first.receiptId(), replay.receiptId());
        assertEquals(1, receipts.size());
        assertEquals(0, service.findOrder("tenant-a", "PO-1").lines().getFirst()
                .receivedQuantity().compareTo(new BigDecimal("2")));
    }

    @Test
    void receiptIdCannotBeReusedWithDifferentQuantity() {
        service.receive("tenant-a", "GR-1", "ledger-a", "PO-1",
                "LINE-1", new BigDecimal("2"), "receiver-1");

        assertThrows(IllegalArgumentException.class, () -> service.receive("tenant-a", "GR-1", "ledger-a", "PO-1",
                "LINE-1", new BigDecimal("3"), "receiver-1"));
        assertEquals(0, service.findOrder("tenant-a", "PO-1").lines().getFirst()
                .receivedQuantity().compareTo(new BigDecimal("2")));
    }

    @Test
    void purchaseOrderLookupIsTenantScoped() {
        assertThrows(IllegalArgumentException.class, () -> service.findOrder("tenant-b", "PO-1"));
    }

    @Test
    void overReceiptIsRejectedWithoutSavingReceipt() {
        assertThrows(IllegalArgumentException.class, () -> service.receive("tenant-a", "GR-OVER", "ledger-a", "PO-1",
                "LINE-1", new BigDecimal("6"), "receiver-1"));
        assertTrue(receipts.isEmpty());
        assertEquals(0, service.findOrder("tenant-a", "PO-1").lines().getFirst()
                .receivedQuantity().compareTo(BigDecimal.ZERO));
    }
}
