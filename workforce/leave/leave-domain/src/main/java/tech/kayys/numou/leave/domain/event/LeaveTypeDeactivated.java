package tech.kayys.numou.leave.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.leave.domain.LeaveTypeId;

import java.time.Instant;
import java.util.UUID;

public record LeaveTypeDeactivated(
        UUID eventId,
        Instant occurredAt,
        LeaveTypeId leaveTypeId
) implements DomainEvent {
    public LeaveTypeDeactivated(LeaveTypeId leaveTypeId) {
        this(UUID.randomUUID(), Instant.now(), leaveTypeId);
    }
    @Override
    public String eventType() {
        return "leave.type.deactivated";
    }
}
