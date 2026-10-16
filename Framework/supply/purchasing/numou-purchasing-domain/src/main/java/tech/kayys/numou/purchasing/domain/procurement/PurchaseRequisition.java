
package tech.kayys.syirkah.purchasing.domain.procurement;

import tech.kayys.syirkah.purchasing.domain.event.procurement.RequisitionApproved;
import tech.kayys.syirkah.purchasing.domain.event.procurement.RequisitionCreated;
import tech.kayys.syirkah.purchasing.domain.event.procurement.RequisitionSubmitted;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class PurchaseRequisition {

    private final String requisitionId;
    private final String tenantId;
    private final String ledgerId;
    private final String departmentId;
    private final List<RequisitionLine> lines = new ArrayList<>();
    private RequisitionStatus status;
    private String approvedBy;

    private final List<Object> domainEvents = new ArrayList<>();

    private PurchaseRequisition(String requisitionId, String tenantId, String ledgerId, String departmentId) {
        this.requisitionId = Objects.requireNonNull(requisitionId, "requisitionId cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.ledgerId = Objects.requireNonNull(ledgerId, "ledgerId cannot be null");
        this.departmentId = Objects.requireNonNull(departmentId, "departmentId cannot be null");
        this.status = RequisitionStatus.DRAFT;

        raise(RequisitionCreated.of(tenantId, ledgerId, requisitionId, departmentId, requisitionId));
    }

    public static PurchaseRequisition create(String requisitionId, String tenantId, String ledgerId, String departmentId) {
        return new PurchaseRequisition(requisitionId, tenantId, ledgerId, departmentId);
    }

    public void addLine(RequisitionLine line) {
        if (status != RequisitionStatus.DRAFT) {
            throw new IllegalStateException("Cannot add line in status: " + status);
        }
        lines.add(Objects.requireNonNull(line));
    }

    public Money calculateTotal() {
        if (lines.isEmpty()) {
            return Money.zero("IDR");
        }
        Money sum = lines.getFirst().estimatedTotal();
        for (int i = 1; i < lines.size(); i++) {
            sum = sum.add(lines.get(i).estimatedTotal());
        }
        return sum;
    }

    public void submit() {
        if (status != RequisitionStatus.DRAFT) {
            throw new IllegalStateException("Requisition is not in DRAFT status");
        }
        if (lines.isEmpty()) {
            throw new IllegalStateException("Cannot submit requisition without line items");
        }
        this.status = RequisitionStatus.SUBMITTED;
        raise(RequisitionSubmitted.of(tenantId, ledgerId, requisitionId, calculateTotal(), requisitionId));
    }

    public void approve(String approvedBy) {
        if (status != RequisitionStatus.SUBMITTED) {
            throw new IllegalStateException("Requisition must be SUBMITTED before approval");
        }
        this.approvedBy = Objects.requireNonNull(approvedBy, "approvedBy cannot be null");
        this.status = RequisitionStatus.APPROVED;
        raise(RequisitionApproved.of(tenantId, ledgerId, requisitionId, approvedBy, requisitionId));
    }

    public String requisitionId() { return requisitionId; }
    public String tenantId() { return tenantId; }
    public String ledgerId() { return ledgerId; }
    public String departmentId() { return departmentId; }
    public RequisitionStatus status() { return status; }
    public List<RequisitionLine> lines() { return Collections.unmodifiableList(lines); }
    public String approvedBy() { return approvedBy; }

    private void raise(Object event) { domainEvents.add(event); }

    public List<Object> pullDomainEvents() {
        List<Object> copy = List.copyOf(domainEvents);
        domainEvents.clear();
        return copy;
    }
}
