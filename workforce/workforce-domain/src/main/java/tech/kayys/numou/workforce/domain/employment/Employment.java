package tech.kayys.numou.workforce.domain.employment;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.workforce.domain.employment.event.EmploymentResumed;
import tech.kayys.numou.workforce.domain.employment.event.EmploymentStarted;
import tech.kayys.numou.workforce.domain.employment.event.EmploymentSuspended;
import tech.kayys.numou.workforce.domain.employment.event.EmploymentTerminated;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Employment aggregate root representing the formal or operational relationship
 * between a Worker and an Organization.
 */
public final class Employment extends AbstractAggregateRoot<EmploymentId> {

    private final WorkerId workerId;
    private final OrganizationRef organization;
    private PositionRef position;
    private final EmploymentType type;

    private EmploymentStatus status;
    private final LocalDate startDate;
    private LocalDate endDate;
    private String terminationReason;

    private Employment(
            EmploymentId id,
            WorkerId workerId,
            OrganizationRef organization,
            PositionRef position,
            EmploymentType type,
            LocalDate startDate
    ) {
        super(id);
        this.workerId = Objects.requireNonNull(workerId, "WorkerId must not be null");
        this.organization = Objects.requireNonNull(organization, "OrganizationRef must not be null");
        this.position = position;
        this.type = Objects.requireNonNull(type, "EmploymentType must not be null");
        this.startDate = Objects.requireNonNull(startDate, "StartDate must not be null");
        this.status = EmploymentStatus.ACTIVE;
    }

    public static Employment start(
            EmploymentId id,
            WorkerId workerId,
            OrganizationRef organization,
            PositionRef position,
            EmploymentType type,
            LocalDate startDate
    ) {
        Objects.requireNonNull(id, "EmploymentId must not be null");
        Employment employment = new Employment(id, workerId, organization, position, type, startDate);
        employment.raise(new EmploymentStarted(id, workerId, organization, position, type, startDate));
        return employment;
    }

    public void suspend(String reason) {
        if (status != EmploymentStatus.ACTIVE) {
            throw new IllegalStateException("Only active employment can be suspended");
        }

        this.status = EmploymentStatus.SUSPENDED;
        this.updatedAt = Instant.now();
        incrementVersion();
        raise(new EmploymentSuspended(id, workerId, reason));
    }

    public void resume() {
        if (status != EmploymentStatus.SUSPENDED) {
            throw new IllegalStateException("Only suspended employment can be resumed");
        }

        this.status = EmploymentStatus.ACTIVE;
        this.updatedAt = Instant.now();
        incrementVersion();
        raise(new EmploymentResumed(id, workerId));
    }

    public void terminate(LocalDate effectiveDate, String reason) {
        Objects.requireNonNull(effectiveDate, "Termination effective date must not be null");
        Objects.requireNonNull(reason, "Termination reason must not be null");

        if (status == EmploymentStatus.TERMINATED) {
            throw new IllegalStateException("Employment is already terminated");
        }

        if (effectiveDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Termination date cannot be before start date");
        }

        this.status = EmploymentStatus.TERMINATED;
        this.endDate = effectiveDate;
        this.terminationReason = reason;
        this.updatedAt = Instant.now();
        incrementVersion();
        raise(new EmploymentTerminated(id, workerId, effectiveDate, reason));
    }

    public void changePosition(PositionRef newPosition) {
        if (status == EmploymentStatus.TERMINATED) {
            throw new IllegalStateException("Cannot change position of terminated employment");
        }
        this.position = newPosition;
        this.updatedAt = Instant.now();
        incrementVersion();
    }

    public WorkerId workerId() {
        return workerId;
    }

    public OrganizationRef organization() {
        return organization;
    }

    public PositionRef position() {
        return position;
    }

    public EmploymentType type() {
        return type;
    }

    public EmploymentStatus status() {
        return status;
    }

    public LocalDate startDate() {
        return startDate;
    }

    public LocalDate endDate() {
        return endDate;
    }

    public String terminationReason() {
        return terminationReason;
    }
}
