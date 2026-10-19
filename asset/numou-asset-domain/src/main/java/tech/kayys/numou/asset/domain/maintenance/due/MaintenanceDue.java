package tech.kayys.numou.asset.domain.maintenance.due;

import tech.kayys.numou.asset.domain.event.maintenance.MaintenanceDueCreated;
import tech.kayys.numou.asset.domain.event.maintenance.MaintenanceDueWorkOrderRequested;
import tech.kayys.numou.asset.domain.maintenance.plan.MaintenancePlanId;
import tech.kayys.numou.asset.domain.maintenance.plan.MaintenanceRuleType;
import tech.kayys.numou.asset.domain.maintenance.schedule.MaintenanceScheduleId;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Persistent due occurrence: a calculated instance of a rule becoming due (ASSET-22 §13).
 *
 * <p>Append-only semantics: corrections are new occurrences; work-order generation is
 * idempotent via the {@code workOrderId} / generation marker.</p>
 */
public final class MaintenanceDue extends AbstractAggregateRoot<MaintenanceDueId> {

    private static final long serialVersionUID = 1L;

    private String tenantId;
    private MaintenanceScheduleId scheduleId;
    private UUID assetId;
    private MaintenancePlanId planId;
    private UUID ruleId;
    private int occurrenceNumber;
    private MaintenanceRuleType ruleType;
    private Instant dueAt;
    private BigDecimal dueMeterValue;
    private MaintenanceDueStatus status;
    private Instant completedAt;
    private UUID workOrderId;

    private MaintenanceDue() { super(); }

    private MaintenanceDue(MaintenanceDueId id, String tenantId, MaintenanceScheduleId scheduleId,
                           UUID assetId, MaintenancePlanId planId, UUID ruleId, int occurrenceNumber) {
        super(id);
        this.tenantId = requireText(tenantId, "tenantId");
        this.scheduleId = Objects.requireNonNull(scheduleId, "scheduleId cannot be null");
        this.assetId = Objects.requireNonNull(assetId, "assetId cannot be null");
        this.planId = Objects.requireNonNull(planId, "planId cannot be null");
        this.ruleId = Objects.requireNonNull(ruleId, "ruleId cannot be null");
        if (occurrenceNumber < 1) {
            throw new BusinessRuleViolation("occurrenceNumber must be >= 1");
        }
        this.occurrenceNumber = occurrenceNumber;
        this.status = MaintenanceDueStatus.UPCOMING;
    }

    public static MaintenanceDue create(MaintenanceDueId id, String tenantId, MaintenanceScheduleId scheduleId,
                                        UUID assetId, MaintenancePlanId planId, UUID ruleId, int occurrenceNumber,
                                        MaintenanceRuleType ruleType, Instant dueAt, BigDecimal dueMeterValue,
                                        MaintenanceDueStatus status, DomainClock clock) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(clock, "clock cannot be null");
        MaintenanceDue due = new MaintenanceDue(id, tenantId, scheduleId, assetId, planId, ruleId, occurrenceNumber);
        due.ruleType = Objects.requireNonNull(ruleType, "ruleType cannot be null");
        due.dueAt = dueAt;
        due.dueMeterValue = dueMeterValue;
        due.status = Objects.requireNonNull(status, "status cannot be null");
        if (dueAt == null && dueMeterValue == null) {
            throw new BusinessRuleViolation("due must have a date, a meter value, or both");
        }
        due.setCreatedAt(clock.now());
        due.setUpdatedAt(clock.now());
        due.raise(new MaintenanceDueCreated(UUID.randomUUID(), clock.now(), id.value(), assetId, status.name()));
        return due;
    }

    public static MaintenanceDue reconstitute(MaintenanceDueId id, String tenantId, MaintenanceScheduleId scheduleId,
                                              UUID assetId, MaintenancePlanId planId, UUID ruleId, int occurrenceNumber,
                                              MaintenanceRuleType ruleType, Instant dueAt, BigDecimal dueMeterValue,
                                              MaintenanceDueStatus status, Instant completedAt, UUID workOrderId) {
        MaintenanceDue due = new MaintenanceDue(id, tenantId, scheduleId, assetId, planId, ruleId, occurrenceNumber);
        due.ruleType = ruleType;
        due.dueAt = dueAt;
        due.dueMeterValue = dueMeterValue;
        due.status = Objects.requireNonNull(status, "status cannot be null");
        due.completedAt = completedAt;
        due.workOrderId = workOrderId;
        return due;
    }

    /** Returns true when another open occurrence already covers the same computed due point. */
    public boolean sameOpenDue(MaintenanceDue other) {
        if (other == null) {
            return false;
        }
        if (!scheduleId.equals(other.scheduleId) || !ruleId.equals(other.ruleId)) {
            return false;
        }
        if (isClosed() || other.isClosed()) {
            return false;
        }
        return Objects.equals(dueAt, other.dueAt) && Objects.equals(dueMeterValue, other.dueMeterValue);
    }

    public boolean isClosed() {
        return status == MaintenanceDueStatus.COMPLETED
                || status == MaintenanceDueStatus.SKIPPED
                || status == MaintenanceDueStatus.CANCELLED;
    }

    public void markOverdue(DomainClock clock) {
        Objects.requireNonNull(clock, "clock cannot be null");
        if (status == MaintenanceDueStatus.DUE || status == MaintenanceDueStatus.UPCOMING) {
            status = MaintenanceDueStatus.OVERDUE;
            touch(clock);
        }
    }

    public void complete(DomainClock clock) {
        Objects.requireNonNull(clock, "clock cannot be null");
        if (isClosed()) {
            throw new InvalidStateException("Due is already closed: " + status);
        }
        status = MaintenanceDueStatus.COMPLETED;
        completedAt = clock.now();
        touch(clock);
    }

    /**
     * Marks work-order generation (ASSET-22 idempotent generation).
     *
     * <p>Taken path: event-via-outbox + generation marker on the due occurrence, so a
     * retry with the same due never produces a second work order even though the
     * ASSET-19 work-order aggregate is owned by a concurrent worker.</p>
     */
    public UUID markWorkOrderGenerated(UUID workOrderId, DomainClock clock) {
        Objects.requireNonNull(clock, "clock cannot be null");
        if (this.workOrderId != null) {
            return this.workOrderId;
        }
        UUID generated = workOrderId == null ? UUID.randomUUID() : workOrderId;
        this.workOrderId = generated;
        touch(clock);
        raise(new MaintenanceDueWorkOrderRequested(UUID.randomUUID(), clock.now(), id.value(), assetId, generated));
        return generated;
    }

    private void touch(DomainClock clock) {
        setUpdatedAt(clock.now());
        incrementVersion();
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new BusinessRuleViolation(field + " cannot be blank");
        }
        return normalized;
    }

    public String tenantId() { return tenantId; }
    public MaintenanceScheduleId scheduleId() { return scheduleId; }
    public UUID assetId() { return assetId; }
    public MaintenancePlanId planId() { return planId; }
    public UUID ruleId() { return ruleId; }
    public int occurrenceNumber() { return occurrenceNumber; }
    public MaintenanceRuleType ruleType() { return ruleType; }
    public Instant dueAt() { return dueAt; }
    public BigDecimal dueMeterValue() { return dueMeterValue; }
    public MaintenanceDueStatus status() { return status; }
    public Instant completedAt() { return completedAt; }
    public UUID workOrderId() { return workOrderId; }
}
