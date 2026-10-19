package tech.kayys.numou.workforce.domain.position;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.position.event.PositionAssigned;
import tech.kayys.numou.workforce.domain.position.event.PositionAssignmentEnded;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * PositionAssignment aggregate root representing the assignment of a Worker to a Position
 * in the context of an Employment relationship.
 */
public final class PositionAssignment extends AbstractAggregateRoot<PositionAssignmentId> {

    private final WorkerId workerId;
    private final EmploymentId employmentId;
    private final PositionId positionId;
    private final LocalDate startDate;

    private LocalDate endDate;
    private PositionAssignmentStatus status;

    private PositionAssignment(
            PositionAssignmentId id,
            WorkerId workerId,
            EmploymentId employmentId,
            PositionId positionId,
            LocalDate startDate
    ) {
        super(id);
        this.workerId = Objects.requireNonNull(workerId, "WorkerId must not be null");
        this.employmentId = Objects.requireNonNull(employmentId, "EmploymentId must not be null");
        this.positionId = Objects.requireNonNull(positionId, "PositionId must not be null");
        this.startDate = Objects.requireNonNull(startDate, "StartDate must not be null");
        this.status = PositionAssignmentStatus.ACTIVE;
    }

    public static PositionAssignment assign(
            PositionAssignmentId id,
            WorkerId workerId,
            EmploymentId employmentId,
            PositionId positionId,
            LocalDate startDate
    ) {
        Objects.requireNonNull(id, "PositionAssignmentId must not be null");
        PositionAssignment assignment = new PositionAssignment(id, workerId, employmentId, positionId, startDate);
        assignment.raise(new PositionAssigned(id, workerId, employmentId, positionId, startDate));
        return assignment;
    }

    public void end(LocalDate effectiveDate) {
        Objects.requireNonNull(effectiveDate, "End date must not be null");

        if (status != PositionAssignmentStatus.ACTIVE) {
            throw new IllegalStateException("Position assignment is not active");
        }

        if (effectiveDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Assignment end date cannot be before start date");
        }

        this.status = PositionAssignmentStatus.ENDED;
        this.endDate = effectiveDate;
        this.updatedAt = Instant.now();
        incrementVersion();
        raise(new PositionAssignmentEnded(id, workerId, positionId, effectiveDate));
    }

    public WorkerId workerId() {
        return workerId;
    }

    public EmploymentId employmentId() {
        return employmentId;
    }

    public PositionId positionId() {
        return positionId;
    }

    public LocalDate startDate() {
        return startDate;
    }

    public LocalDate endDate() {
        return endDate;
    }

    public PositionAssignmentStatus status() {
        return status;
    }
}
