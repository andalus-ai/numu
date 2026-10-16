package tech.kayys.numou.scheduling.domain;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.ref.ResourceRef;
import tech.kayys.numou.scheduling.domain.event.ShiftCancelled;
import tech.kayys.numou.scheduling.domain.event.ShiftCompleted;
import tech.kayys.numou.scheduling.domain.event.ShiftScheduled;
import tech.kayys.numou.scheduling.domain.event.ShiftStarted;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;

public final class Shift extends AbstractAggregateRoot<ShiftId> {

    private final ScheduleId scheduleId;
    private final ResourceRef assignee;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String location;
    private ShiftStatus status;

    private Shift(ShiftId id, ScheduleId scheduleId, ResourceRef assignee,
                  LocalDateTime startTime, LocalDateTime endTime, String location) {
        super(id);
        this.scheduleId = Objects.requireNonNull(scheduleId, "scheduleId must not be null");
        this.assignee = Objects.requireNonNull(assignee, "assignee must not be null");
        this.startTime = Objects.requireNonNull(startTime, "startTime must not be null");
        this.endTime = Objects.requireNonNull(endTime, "endTime must not be null");
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
        this.location = location;
        this.status = ShiftStatus.SCHEDULED;
    }

    public static Shift schedule(ShiftId id, ScheduleId scheduleId, ResourceRef assignee,
                                  LocalDateTime startTime, LocalDateTime endTime, String location) {
        Shift shift = new Shift(id, scheduleId, assignee, startTime, endTime, location);
        shift.raise(new ShiftScheduled(id, scheduleId, assignee, startTime, endTime));
        return shift;
    }

    public void start() {
        if (status != ShiftStatus.SCHEDULED) {
            throw new IllegalStateException("Only SCHEDULED shifts can be started");
        }
        status = ShiftStatus.STARTED;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new ShiftStarted(getId(), scheduleId, assignee));
    }

    public void complete() {
        if (status != ShiftStatus.STARTED) {
            throw new IllegalStateException("Only STARTED shifts can be completed");
        }
        status = ShiftStatus.COMPLETED;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new ShiftCompleted(getId(), scheduleId, assignee));
    }

    public void cancel() {
        if (status == ShiftStatus.COMPLETED || status == ShiftStatus.CANCELLED) {
            throw new IllegalStateException("Cannot cancel a " + status + " shift");
        }
        status = ShiftStatus.CANCELLED;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new ShiftCancelled(getId(), scheduleId, assignee));
    }

    public ScheduleId getScheduleId() { return scheduleId; }
    public ResourceRef getAssignee() { return assignee; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public String getLocation() { return location; }
    public ShiftStatus getStatus() { return status; }
}
