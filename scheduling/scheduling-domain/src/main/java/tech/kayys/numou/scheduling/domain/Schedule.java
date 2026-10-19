package tech.kayys.numou.scheduling.domain;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.scheduling.domain.event.ScheduleCreated;
import tech.kayys.numou.scheduling.domain.event.SchedulePublished;
import tech.kayys.numou.scheduling.domain.event.ScheduleCancelled;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public final class Schedule extends AbstractAggregateRoot<ScheduleId> {

    private final TenantId tenantId;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private String name;
    private ScheduleStatus status;

    private Schedule(ScheduleId id, TenantId tenantId, String name,
                     LocalDate startDate, LocalDate endDate) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.startDate = Objects.requireNonNull(startDate, "startDate must not be null");
        this.endDate = Objects.requireNonNull(endDate, "endDate must not be null");
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate cannot be before startDate");
        }
        this.status = ScheduleStatus.DRAFT;
    }

    public static Schedule create(ScheduleId id, TenantId tenantId, String name,
                                   LocalDate startDate, LocalDate endDate) {
        Schedule s = new Schedule(id, tenantId, name, startDate, endDate);
        s.raise(new ScheduleCreated(id, tenantId, name, startDate, endDate));
        return s;
    }

    public void publish() {
        if (status != ScheduleStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT schedules can be published");
        }
        status = ScheduleStatus.PUBLISHED;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new SchedulePublished(getId(), tenantId));
    }

    public void cancel() {
        if (status == ScheduleStatus.CANCELLED) return;
        status = ScheduleStatus.CANCELLED;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new ScheduleCancelled(getId(), tenantId));
    }

    public TenantId getTenantId() { return tenantId; }
    public String getName() { return name; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public ScheduleStatus getStatus() { return status; }
    public boolean isDraft() { return status == ScheduleStatus.DRAFT; }
    public boolean isPublished() { return status == ScheduleStatus.PUBLISHED; }
}
