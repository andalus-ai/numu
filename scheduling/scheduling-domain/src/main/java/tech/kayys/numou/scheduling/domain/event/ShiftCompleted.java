package tech.kayys.numou.scheduling.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.ref.ResourceRef;
import tech.kayys.numou.scheduling.domain.ScheduleId;
import tech.kayys.numou.scheduling.domain.ShiftId;

import java.time.Instant;
import java.util.UUID;

public record ShiftCompleted(
        UUID eventId,
        Instant occurredAt,
        ShiftId shiftId,
        ScheduleId scheduleId,
        ResourceRef assignee
) implements DomainEvent {
    public ShiftCompleted(ShiftId shiftId, ScheduleId scheduleId, ResourceRef assignee) {
        this(UUID.randomUUID(), Instant.now(), shiftId, scheduleId, assignee);
    }
    @Override
    public String eventType() {
        return "scheduling.shift.completed";
    }
}
