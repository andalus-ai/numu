package tech.kayys.numou.workforce.domain.position.policy;

import tech.kayys.numou.workforce.domain.employment.Employment;
import tech.kayys.numou.workforce.domain.employment.EmploymentStatus;
import tech.kayys.numou.workforce.domain.position.Position;
import tech.kayys.numou.workforce.domain.position.PositionStatus;
import tech.kayys.numou.workforce.domain.worker.Worker;
import tech.kayys.numou.workforce.domain.worker.WorkerStatus;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Domain policy enforcing assignment rules (W-04):
 * Rule A - Position must be active
 * Rule B - Worker must exist and have active employment
 * Rule C - Position organization must match employment organization
 * Rule D - Assignment startDate cannot be before employment startDate
 * Rule E - Assignment cannot start after employment endDate
 */
public final class PositionAssignmentPolicy {

    public void validateAssignment(
            Worker worker,
            Employment employment,
            Position position,
            LocalDate startDate
    ) {
        Objects.requireNonNull(worker, "Worker is required");
        Objects.requireNonNull(employment, "Employment is required");
        Objects.requireNonNull(position, "Position is required");
        Objects.requireNonNull(startDate, "Assignment start date is required");

        // Rule A - Position must be active
        if (position.status() != PositionStatus.ACTIVE) {
            throw new IllegalStateException("POSITION_NOT_ACTIVE: Position is not active");
        }

        // Rule B1 - Worker must be active
        if (worker.status() != WorkerStatus.ACTIVE) {
            throw new IllegalStateException("WORKER_NOT_ACTIVE: Worker is not active");
        }

        // Rule B2 - Worker must match employment
        if (!employment.workerId().equals(worker.id())) {
            throw new IllegalArgumentException("EMPLOYMENT_WORKER_MISMATCH: Worker does not own employment");
        }

        // Rule B3 - Employment must be active
        if (employment.status() != EmploymentStatus.ACTIVE) {
            throw new IllegalStateException("EMPLOYMENT_NOT_ACTIVE: Employment is not active");
        }

        // Rule C - Organization must match
        if (!position.organization().equals(employment.organization())) {
            throw new IllegalArgumentException("ORGANIZATION_MISMATCH: Position and Employment must belong to same organization");
        }

        // Rule D - Assignment date cannot be before employment start date
        if (startDate.isBefore(employment.startDate())) {
            throw new IllegalArgumentException("DATE_BEFORE_EMPLOYMENT: Assignment start date cannot precede employment start date");
        }

        // Rule E - Assignment date cannot be after employment end date (if terminated or bounded)
        if (employment.endDate() != null && startDate.isAfter(employment.endDate())) {
            throw new IllegalArgumentException("DATE_AFTER_EMPLOYMENT_END: Assignment start date cannot exceed employment end date");
        }
    }
}
