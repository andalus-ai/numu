package tech.kayys.numou.purchasing.application;

import tech.kayys.numou.purchasing.domain.procurement.PurchaseRequisition;
import tech.kayys.numou.purchasing.domain.procurement.RequisitionLine;
import tech.kayys.numou.purchasing.spi.PurchaseRequisitionRepository;

import java.util.Objects;

/** Application use cases for a purchasing-owned requisition aggregate. */
public final class RequisitionService {
    private final PurchaseRequisitionRepository repository;

    public RequisitionService(PurchaseRequisitionRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository is required");
    }

    public PurchaseRequisition createDraft(String tenantId, String requisitionId, String ledgerId, String departmentId) {
        require(tenantId, "tenantId");
        require(requisitionId, "requisitionId");
        require(ledgerId, "ledgerId");
        require(departmentId, "departmentId");
        if (repository.findByTenantAndId(tenantId, requisitionId).isPresent()) {
            throw new IllegalStateException("Requisition already exists for tenant and id");
        }
        PurchaseRequisition requisition = PurchaseRequisition.create(requisitionId, tenantId, ledgerId, departmentId);
        repository.save(requisition);
        return requisition;
    }

    public PurchaseRequisition addLine(String tenantId, String requisitionId, RequisitionLine line) {
        PurchaseRequisition requisition = find(tenantId, requisitionId);
        requisition.addLine(Objects.requireNonNull(line, "line is required"));
        repository.save(requisition);
        return requisition;
    }

    public PurchaseRequisition submit(String tenantId, String requisitionId) {
        PurchaseRequisition requisition = find(tenantId, requisitionId);
        requisition.submit();
        repository.save(requisition);
        return requisition;
    }

    public PurchaseRequisition approve(String tenantId, String requisitionId, String actorId) {
        require(actorId, "actorId");
        PurchaseRequisition requisition = find(tenantId, requisitionId);
        requisition.approve(actorId);
        repository.save(requisition);
        return requisition;
    }

    public PurchaseRequisition find(String tenantId, String requisitionId) {
        require(tenantId, "tenantId");
        require(requisitionId, "requisitionId");
        return repository.findByTenantAndId(tenantId, requisitionId)
                .orElseThrow(() -> new IllegalArgumentException("Requisition not found for tenant"));
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}