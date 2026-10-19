package tech.kayys.numou.scheduling.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.ref.ResourceRef;
import tech.kayys.numou.scheduling.domain.ScheduleId;
import tech.kayys.numou.scheduling.domain.ShiftId;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record ShiftScheduled(
        UUID eventId,
        Instant occurredAt,
        ShiftId shiftId,
        ScheduleId scheduleId,
        ResourceRef assignee,
        LocalDateTime startTime,
        LocalDateTime endTime
) implements DomainEvent {
    public ShiftScheduled(ShiftId shiftId, ScheduleId scheduleId, ResourceRef assignee,
                          LocalDateTime startTime, LocalDateTime endTime) {
        this(UUID.randomUUID(), Instant.now(), shiftId, scheduleId, assignee, startTime, endTime);
    }
    @Override
    public String eventType() {
        return "scheduling.shift.scheduled";
    }
}
