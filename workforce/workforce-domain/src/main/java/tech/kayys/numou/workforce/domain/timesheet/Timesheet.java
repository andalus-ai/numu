package tech.kayys.numou.workforce.domain.timesheet;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.timesheet.event.TimesheetApproved;
import tech.kayys.numou.workforce.domain.timesheet.event.TimesheetCreated;
import tech.kayys.numou.workforce.domain.timesheet.event.TimesheetEntryAdded;
import tech.kayys.numou.workforce.domain.timesheet.event.TimesheetRejected;
import tech.kayys.numou.workforce.domain.timesheet.event.TimesheetSubmitted;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Timesheet aggregate root — classifies worked time against business contexts
 * (projects, work orders, customers).
 *
 * <p>Lifecycle: {@code DRAFT} → {@code SUBMITTED} → {@code APPROVED} | {@code REJECTED} | {@code LOCKED}.
 */
public final class Timesheet extends AbstractAggregateRoot<TimesheetId> {

    private final WorkerId workerId;
    private final EmploymentId employmentId;
    private final LocalDate periodStart;
    private final LocalDate periodEnd;
    private TimesheetStatus status;
    private final List<TimesheetEntry> entries;
    private String approvedBy;
    private String rejectionReason;

    private Timesheet(TimesheetId id, WorkerId workerId, EmploymentId employmentId,
                      LocalDate periodStart, LocalDate periodEnd) {
        super(id);
        this.workerId = Objects.requireNonNull(workerId, "workerId must not be null");
        this.employmentId = Objects.requireNonNull(employmentId, "employmentId must not be null");
        this.periodStart = Objects.requireNonNull(periodStart, "periodStart must not be null");
        this.periodEnd = Objects.requireNonNull(periodEnd, "periodEnd must not be null");
        if (periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("Period end cannot be before period start");
        }
        this.status = TimesheetStatus.DRAFT;
        this.entries = new ArrayList<>();
    }

    public static Timesheet create(TimesheetId id, WorkerId workerId, EmploymentId employmentId,
                                   LocalDate periodStart, LocalDate periodEnd) {
        Timesheet timesheet = new Timesheet(id, workerId, employmentId, periodStart, periodEnd);
        timesheet.raise(new TimesheetCreated(id, workerId, employmentId, periodStart, periodEnd));
        return timesheet;
    }

    public void addEntry(TimesheetEntry entry) {
        if (status != TimesheetStatus.DRAFT) {
            throw new IllegalStateException("Cannot modify timesheet in status: " + status);
        }
        Objects.requireNonNull(entry, "entry must not be null");
        if (entry.date().isBefore(periodStart) || entry.date().isAfter(periodEnd)) {
            throw new IllegalArgumentException("Entry date is outside timesheet period");
        }
        entries.add(entry);
        incrementVersion();
        updatedAt = Instant.now();
        raise(new TimesheetEntryAdded(getId(), entry.id(), entry.date(), entry.duration(), entry.context()));
    }

    public void submit() {
        if (status != TimesheetStatus.DRAFT) {
            throw new IllegalStateException("Only draft timesheets can be submitted");
        }
        this.status = TimesheetStatus.SUBMITTED;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new TimesheetSubmitted(getId(), workerId, totalDuration()));
    }

    public void approve(String approver) {
        if (status != TimesheetStatus.SUBMITTED) {
            throw new IllegalStateException("Only submitted timesheets can be approved");
        }
        this.status = TimesheetStatus.APPROVED;
        this.approvedBy = approver;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new TimesheetApproved(getId(), workerId, approver));
    }

    public void reject(String rejectedBy, String reason) {
        if (status != TimesheetStatus.SUBMITTED) {
            throw new IllegalStateException("Only submitted timesheets can be rejected");
        }
        this.status = TimesheetStatus.REJECTED;
        this.rejectionReason = reason;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new TimesheetRejected(getId(), workerId, rejectedBy, reason));
    }

    public Duration totalDuration() {
        return entries.stream()
                .map(TimesheetEntry::duration)
                .reduce(Duration.ZERO, Duration::plus);
    }

    public WorkerId getWorkerId() { return workerId; }
    public EmploymentId getEmploymentId() { return employmentId; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public TimesheetStatus getStatus() { return status; }
    public List<TimesheetEntry> getEntries() { return Collections.unmodifiableList(entries); }
    public String getApprovedBy() { return approvedBy; }
    public String getRejectionReason() { return rejectionReason; }
}
