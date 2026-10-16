package tech.kayys.syirkah.construction.domain.workforce;

import tech.kayys.syirkah.construction.domain.workforce.event.LaborRequirementCreated;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class LaborRequirement extends AbstractAggregateRoot<LaborRequirementId> {
    private final UUID projectId;
    private final UUID siteId;
    private final String trade;
    private final int workersRequired;
    private final LocalDate requiredDate;
    private final List<LaborAssignment> assignments = new ArrayList<>();
    private LaborRequirementStatus status;

    private LaborRequirement(LaborRequirementId id, UUID projectId, UUID siteId, String trade, int workersRequired, LocalDate requiredDate) {
        super(id);
        this.projectId = Objects.requireNonNull(projectId);
        this.siteId = Objects.requireNonNull(siteId);
        this.trade = Objects.requireNonNull(trade);
        this.workersRequired = workersRequired;
        this.requiredDate = Objects.requireNonNull(requiredDate);
        this.status = LaborRequirementStatus.REQUESTED;
    }

    public static LaborRequirement create(UUID projectId, UUID siteId, String trade, int workersRequired, LocalDate requiredDate) {
        var req = new LaborRequirement(LaborRequirementId.generate(), projectId, siteId, trade, workersRequired, requiredDate);
        req.raise(new LaborRequirementCreated(UUID.randomUUID(), Instant.now(), req.id().value(), projectId, trade));
        return req;
    }

    public void assignWorker(LaborAssignment assignment) {
        assignments.add(Objects.requireNonNull(assignment));
        if (assignments.size() >= workersRequired) {
            status = LaborRequirementStatus.ASSIGNED;
        }
    }

    public UUID projectId() { return projectId; }
    public UUID siteId() { return siteId; }
    public String trade() { return trade; }
    public int workersRequired() { return workersRequired; }
    public LocalDate requiredDate() { return requiredDate; }
    public List<LaborAssignment> assignments() { return List.copyOf(assignments); }
    public LaborRequirementStatus status() { return status; }
}
