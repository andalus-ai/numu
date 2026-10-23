package tech.kayys.numou.planning.application;

import tech.kayys.numou.planning.application.port.ScopeQuantityRepository;
import tech.kayys.numou.planning.domain.*;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Connects neutral scope quantities to schedule activities without construction-specific dependencies. */
public final class AssessQuantityBasedActivityProgress {
    private final ScopeQuantityRepository quantities;
    private final GetAcceptedScopeQuantity acceptedQuantities;

    public AssessQuantityBasedActivityProgress(ScopeQuantityRepository quantities, GetAcceptedScopeQuantity acceptedQuantities) {
        this.quantities = Objects.requireNonNull(quantities, "Quantity repository cannot be null");
        this.acceptedQuantities = Objects.requireNonNull(acceptedQuantities, "Accepted quantity resolver cannot be null");
    }

    public QuantityBasedProgressAssessment execute(UUID tenantId, UUID definitionId, ScheduleActivity activity, LocalDate asOfDate) {
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(definitionId, "Quantity definition id cannot be null");
        Objects.requireNonNull(activity, "Activity cannot be null");
        Objects.requireNonNull(asOfDate, "As-of date cannot be null");
        ScopeQuantityDefinition definition = quantities.findDefinition(tenantId, definitionId)
                .orElseThrow(() -> new IllegalArgumentException("Quantity definition not found for tenant"));
        WorkItemReference workItem = activity.linkedWorkItem()
                .orElseThrow(() -> new IllegalArgumentException("Activity must reference the quantity's work item"));
        if (!definition.scopeType().equals(workItem.type()) || !definition.scopeId().equals(workItem.id()))
            throw new IllegalArgumentException("Activity work-item reference does not match quantity definition scope");
        AcceptedQuantityProjection accepted = acceptedQuantities.execute(tenantId, definitionId, asOfDate);
        return QuantityBasedProgressAssessment.assess(tenantId, definitionId, accepted.acceptedMeasurementId(),
                activity, asOfDate, definition.plannedQuantity(), accepted.acceptedQuantity());
    }
}
