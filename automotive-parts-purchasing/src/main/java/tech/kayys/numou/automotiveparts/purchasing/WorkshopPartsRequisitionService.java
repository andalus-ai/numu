package tech.kayys.numou.automotiveparts.purchasing;

import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.purchasing.application.RequisitionService;
import tech.kayys.numou.purchasing.domain.procurement.PurchaseRequisition;
import tech.kayys.numou.purchasing.domain.procurement.RequisitionLine;
import tech.kayys.numou.purchasing.domain.procurement.RequisitionStatus;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Coordinates workshop external-parts demand into a Purchasing-owned requisition.
 * Persistence must enforce tenant-scoped uniqueness and serialize concurrent writes for
 * the demand ID; this adapter alone cannot provide cross-process exactly-once execution.
 */
public final class WorkshopPartsRequisitionService {
    private final RequisitionService requisitions;

    public WorkshopPartsRequisitionService(RequisitionService requisitions) {
        this.requisitions = Objects.requireNonNull(requisitions, "requisitions is required");
    }

    /** Creates or recovers a one-line requisition and submits it for normal approval. */
    public PurchaseRequisition requestExternalPart(WorkshopPartsDemand demand) {
        Objects.requireNonNull(demand, "demand is required");
        PurchaseRequisition requisition;
        try {
            requisition = requisitions.find(demand.tenantId(), demand.demandId());
        } catch (IllegalArgumentException notFound) {
            requisition = requisitions.createDraft(demand.tenantId(), demand.demandId(),
                    demand.ledgerId(), demand.departmentId());
        }

        validateHeader(requisition, demand);
        String lineId = demand.demandId() + ":part";
        RequisitionLine expected = new RequisitionLine(lineId, description(demand),
                demand.quantity(), demand.estimatedUnitPrice());
        RequisitionLine existing = requisition.lines().stream()
                .filter(line -> line.lineId().equals(lineId)).findFirst().orElse(null);

        if (existing == null) {
            if (requisition.status() != RequisitionStatus.DRAFT || !requisition.lines().isEmpty()) {
                throw new IllegalStateException("Demand requisition is not in a recoverable draft state");
            }
            requisition = requisitions.addLine(demand.tenantId(), demand.demandId(), expected);
        } else if (!sameLine(existing, expected) || requisition.lines().size() != 1) {
            throw new IllegalStateException("Demand ID was already used with a different parts request");
        }

        if (requisition.status() == RequisitionStatus.DRAFT) {
            requisition = requisitions.submit(demand.tenantId(), demand.demandId());
        }
        if (requisition.status() != RequisitionStatus.SUBMITTED
                && requisition.status() != RequisitionStatus.APPROVED
                && requisition.status() != RequisitionStatus.CONVERTED_TO_PO) {
            throw new IllegalStateException("Demand requisition is in a terminal or unsupported state");
        }
        return requisition;
    }

    private static void validateHeader(PurchaseRequisition requisition, WorkshopPartsDemand demand) {
        if (!requisition.tenantId().equals(demand.tenantId())
                || !requisition.ledgerId().equals(demand.ledgerId())
                || !requisition.departmentId().equals(demand.departmentId())) {
            throw new IllegalStateException("Demand ID was already used with different purchasing context");
        }
    }

    private static boolean sameLine(RequisitionLine left, RequisitionLine right) {
        return left.lineId().equals(right.lineId())
                && left.itemDescription().equals(right.itemDescription())
                && left.quantity().compareTo(right.quantity()) == 0
                && left.estimatedUnitPrice().equals(right.estimatedUnitPrice());
    }

    private static String description(WorkshopPartsDemand demand) {
        return "Workshop part " + demand.partReference() + " | work order " + demand.workOrderReference()
                + " | line " + demand.workOrderLineReference() + " | UOM " + demand.unitOfMeasure()
                + " | fitment snapshot " + demand.fitmentSnapshotReference();
    }

    public record WorkshopPartsDemand(String tenantId, String demandId, String ledgerId, String departmentId,
            String workOrderReference, String workOrderLineReference, String partReference,
            String fitmentSnapshotReference, BigDecimal quantity, String unitOfMeasure, Money estimatedUnitPrice) {
        public WorkshopPartsDemand {
            require(tenantId, "tenantId"); require(demandId, "demandId"); require(ledgerId, "ledgerId");
            require(departmentId, "departmentId"); require(workOrderReference, "workOrderReference");
            require(workOrderLineReference, "workOrderLineReference"); require(partReference, "partReference");
            require(fitmentSnapshotReference, "fitmentSnapshotReference"); require(unitOfMeasure, "unitOfMeasure");
            Objects.requireNonNull(estimatedUnitPrice, "estimatedUnitPrice is required");
            if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("quantity must be positive");
        }
        private static void require(String value, String name) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        }
    }
}