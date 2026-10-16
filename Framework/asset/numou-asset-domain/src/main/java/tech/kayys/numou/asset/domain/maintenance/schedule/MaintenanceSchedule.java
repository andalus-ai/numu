package tech.kayys.syirkah.asset.domain.maintenance.schedule;

import tech.kayys.syirkah.asset.domain.event.maintenance.MaintenanceScheduleCreated;
import tech.kayys.syirkah.asset.domain.maintenance.plan.MaintenancePlanId;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.foundation.domain.time.DomainClock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Binds a reusable plan to one asset (ASSET-22 §§9-11).
 */
public final class MaintenanceSchedule extends AbstractAggregateRoot<MaintenanceScheduleId> {

    private static final long serialVersionUID = 1L;

    private String tenantId;
    private UUID assetId;
    private MaintenancePlanId planId;
    private MaintenanceScheduleStatus status;
    private Instant effectiveFrom;
    private Instant effectiveUntil;
    private Instant lastServiceAt;
    private final List<MeterScheduleBaseline> baselines = new ArrayList<>();

    private MaintenanceSchedule() { super(); }

    private MaintenanceSchedule(MaintenanceScheduleId id, String tenantId, UUID assetId, MaintenancePlanId planId) {
        super(id);
        this.tenantId = requireText(tenantId, "tenantId");
        this.assetId = Objects.requireNonNull(assetId, "assetId cannot be null");
        this.planId = Objects.requireNonNull(planId, "planId cannot be null");
        this.status = MaintenanceScheduleStatus.ACTIVE;
    }

    public static MaintenanceSchedule create(MaintenanceScheduleId id, String tenantId, UUID assetId,
                                             MaintenancePlanId planId, Instant effectiveFrom,
                                             Instant lastServiceAt, List<MeterScheduleBaseline> baselines,
                                             DomainClock clock) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(clock, "clock cannot be null");
        if (effectiveFrom == null) {
            throw new BusinessRuleViolation("effectiveFrom cannot be null");
        }
        MaintenanceSchedule schedule = new MaintenanceSchedule(id, tenantId, assetId, planId);
        schedule.effectiveFrom = effectiveFrom;
        schedule.lastServiceAt = lastServiceAt == null ? effectiveFrom : lastServiceAt;
        if (baselines != null) {
            schedule.baselines.addAll(baselines);
        }
        schedule.setCreatedAt(clock.now());
        schedule.setUpdatedAt(clock.now());
        schedule.raise(new MaintenanceScheduleCreated(UUID.randomUUID(), clock.now(), id.value(), assetId, planId.value()));
        return schedule;
    }

    public static MaintenanceSchedule reconstitute(MaintenanceScheduleId id, String tenantId, UUID assetId,
                                                   MaintenancePlanId planId, MaintenanceScheduleStatus status,
                                                   Instant effectiveFrom, Instant effectiveUntil,
                                                   Instant lastServiceAt, List<MeterScheduleBaseline> baselines) {
        MaintenanceSchedule schedule = new MaintenanceSchedule(id, tenantId, assetId, planId);
        schedule.status = Objects.requireNonNull(status, "status cannot be null");
        schedule.effectiveFrom = effectiveFrom;
        schedule.effectiveUntil = effectiveUntil;
        schedule.lastServiceAt = lastServiceAt;
        if (baselines != null) {
            schedule.baselines.addAll(baselines);
        }
        return schedule;
    }

    public void recordService(Instant servicedAt, DomainClock clock) {
        Objects.requireNonNull(servicedAt, "servicedAt cannot be null");
        Objects.requireNonNull(clock, "clock cannot be null");
        this.lastServiceAt = servicedAt;
        setUpdatedAt(clock.now());
        incrementVersion();
    }

    public void suspend(DomainClock clock) {
        requireStatus(MaintenanceScheduleStatus.ACTIVE, "suspend");
        status = MaintenanceScheduleStatus.SUSPENDED;
        touch(clock);
    }

    public void resume(DomainClock clock) {
        requireStatus(MaintenanceScheduleStatus.SUSPENDED, "resume");
        status = MaintenanceScheduleStatus.ACTIVE;
        touch(clock);
    }

    public void cancel(DomainClock clock) {
        requireStatus(MaintenanceScheduleStatus.ACTIVE, "cancel");
        status = MaintenanceScheduleStatus.CANCELLED;
        touch(clock);
    }

    public void complete(DomainClock clock) {
        requireStatus(MaintenanceScheduleStatus.ACTIVE, "complete");
        status = MaintenanceScheduleStatus.COMPLETED;
        touch(clock);
    }

    private void requireStatus(MaintenanceScheduleStatus expected, String operation) {
        Objects.requireNonNull(expected, "expected cannot be null");
        if (status != expected) {
            throw new InvalidStateException("Schedule cannot " + operation + " from " + status);
        }
    }

    private void touch(DomainClock clock) {
        Objects.requireNonNull(clock, "clock cannot be null");
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
    public UUID assetId() { return assetId; }
    public MaintenancePlanId planId() { return planId; }
    public MaintenanceScheduleStatus status() { return status; }
    public Instant effectiveFrom() { return effectiveFrom; }
    public Instant effectiveUntil() { return effectiveUntil; }
    public Instant lastServiceAt() { return lastServiceAt; }
    public List<MeterScheduleBaseline> baselines() { return Collections.unmodifiableList(baselines); }
}
