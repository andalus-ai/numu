
package tech.kayys.syirkah.purchasing.domain.procurement;

import tech.kayys.syirkah.purchasing.domain.event.procurement.PurchaseOrderApproved;
import tech.kayys.syirkah.purchasing.domain.event.procurement.PurchaseOrderCreated;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class PurchaseOrder {

    private final String poId;
    private final String tenantId;
    private final String ledgerId;
    private final String vendorId;
    private final List<PoLine> lines = new ArrayList<>();
    private PoStatus status;
    private String approvedBy;

    private final List<Object> domainEvents = new ArrayList<>();

    private PurchaseOrder(String poId, String tenantId, String ledgerId, String vendorId) {
        this.poId = Objects.requireNonNull(poId, "poId cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.ledgerId = Objects.requireNonNull(ledgerId, "ledgerId cannot be null");
        this.vendorId = Objects.requireNonNull(vendorId, "vendorId cannot be null");
        this.status = PoStatus.DRAFT;

        raise(PurchaseOrderCreated.of(tenantId, ledgerId, poId, vendorId, poId));
    }

    public static PurchaseOrder create(String poId, String tenantId, String ledgerId, String vendorId) {
        return new PurchaseOrder(poId, tenantId, ledgerId, vendorId);
    }

    public void addLine(PoLine line) {
        if (status != PoStatus.DRAFT) {
            throw new IllegalStateException("Cannot add line in status: " + status);
        }
        lines.add(Objects.requireNonNull(line));
    }

    public Money totalAmount() {
        if (lines.isEmpty()) {
            return Money.zero("IDR");
        }
        Money sum = lines.getFirst().lineTotal();
        for (int i = 1; i < lines.size(); i++) {
            sum = sum.add(lines.get(i).lineTotal());
        }
        return sum;
    }

    public void approve(String approvedBy) {
        if (status != PoStatus.DRAFT) {
            throw new IllegalStateException("Purchase Order must be in DRAFT to approve");
        }
        if (lines.isEmpty()) {
            throw new IllegalStateException("Cannot approve empty purchase order");
        }
        this.approvedBy = Objects.requireNonNull(approvedBy);
        this.status = PoStatus.APPROVED;
        raise(PurchaseOrderApproved.of(tenantId, ledgerId, poId, vendorId, totalAmount(), approvedBy, poId));
    }

    public void sendToVendor() {
        if (status != PoStatus.APPROVED) {
            throw new IllegalStateException("PO must be APPROVED before sending to vendor");
        }
        this.status = PoStatus.SENT_TO_VENDOR;
    }

    public void recordReceipt(String lineId, BigDecimal receivedQty) {
        if (status != PoStatus.SENT_TO_VENDOR && status != PoStatus.PARTIALLY_RECEIVED) {
            throw new IllegalStateException("Cannot receive items in status: " + status);
        }
        boolean found = false;
        for (int i = 0; i < lines.size(); i++) {
            PoLine line = lines.get(i);
            if (line.lineId().equals(lineId)) {
                lines.set(i, line.withReceived(receivedQty));
                found = true;
                break;
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Line ID not found in PO: " + lineId);
        }

        boolean allReceived = lines.stream().allMatch(PoLine::isFullyReceived);
        this.status = allReceived ? PoStatus.FULLY_RECEIVED : PoStatus.PARTIALLY_RECEIVED;
    }

    public String poId() { return poId; }
    public String tenantId() { return tenantId; }
    public String ledgerId() { return ledgerId; }
    public String vendorId() { return vendorId; }
    public PoStatus status() { return status; }
    public List<PoLine> lines() { return Collections.unmodifiableList(lines); }
    public String approvedBy() { return approvedBy; }

    private void raise(Object event) { domainEvents.add(event); }

    public List<Object> pullDomainEvents() {
        List<Object> copy = List.copyOf(domainEvents);
        domainEvents.clear();
        return copy;
    }
}
