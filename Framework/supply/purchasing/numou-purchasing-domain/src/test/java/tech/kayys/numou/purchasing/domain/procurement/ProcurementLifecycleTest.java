package tech.kayys.syirkah.purchasing.domain.procurement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Procure-to-Pay Aggregates Lifecycle Test")
class ProcurementLifecycleTest {

    private final String tenantId = "tenant-kayys";
    private final String ledgerId = "PRIMARY";

    @Test
    @DisplayName("PurchaseRequisition creation, submission and approval")
    void testRequisitionLifecycle() {
        PurchaseRequisition req = PurchaseRequisition.create("REQ-001", tenantId, ledgerId, "IT-DEPT");
        req.addLine(new RequisitionLine("L1", "MacBook Pro", BigDecimal.valueOf(2), Money.of(new BigDecimal("30000000"), "IDR")));

        assertEquals(RequisitionStatus.DRAFT, req.status());
        assertEquals(0, new BigDecimal("60000000").compareTo(req.calculateTotal().amount()));

        req.submit();
        assertEquals(RequisitionStatus.SUBMITTED, req.status());

        req.approve("MANAGER-01");
        assertEquals(RequisitionStatus.APPROVED, req.status());
        assertEquals("MANAGER-01", req.approvedBy());
    }

    @Test
    @DisplayName("PurchaseOrder approval and item receiving")
    void testPurchaseOrderLifecycle() {
        PurchaseOrder po = PurchaseOrder.create("PO-001", tenantId, ledgerId, "VENDOR-APPLE");
        po.addLine(PoLine.of("POL-1", "MBP-16", "MacBook Pro 16", BigDecimal.valueOf(5), Money.of(new BigDecimal("35000000"), "IDR")));

        assertEquals(PoStatus.DRAFT, po.status());
        assertEquals(0, new BigDecimal("175000000").compareTo(po.totalAmount().amount()));

        po.approve("CFO-01");
        assertEquals(PoStatus.APPROVED, po.status());

        po.sendToVendor();
        assertEquals(PoStatus.SENT_TO_VENDOR, po.status());

        // Receive partial
        po.recordReceipt("POL-1", BigDecimal.valueOf(3));
        assertEquals(PoStatus.PARTIALLY_RECEIVED, po.status());

        // Receive remaining
        po.recordReceipt("POL-1", BigDecimal.valueOf(2));
        assertEquals(PoStatus.FULLY_RECEIVED, po.status());
    }
}
