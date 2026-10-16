package tech.kayys.syirkah.accounting.domain.audit;

import java.time.LocalDate;
import java.util.Objects;

public record AuditPlanItem(
        AuditableEntityId entityId,
        LocalDate plannedStartDate,
        LocalDate plannedEndDate,
        String leadAuditor,
        int estimatedHours,
        String rationale
) {
    public AuditPlanItem {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(plannedStartDate, "plannedStartDate");
        Objects.requireNonNull(plannedEndDate, "plannedEndDate");
        Objects.requireNonNull(leadAuditor, "leadAuditor");
        Objects.requireNonNull(rationale, "rationale");
        if (plannedEndDate.isBefore(plannedStartDate)) {
            throw new AuditViolationException("plannedEndDate must be on or after plannedStartDate");
        }
        if (estimatedHours < 0) {
            throw new AuditViolationException("estimatedHours must be >= 0");
        }
    }
}
