package tech.kayys.numou.leave.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.leave.domain.LeaveRequestId;

import java.time.Instant;
import java.util.UUID;

public record LeaveRequestCancelled(
        UUID eventId,
        Instant occurredAt,
        LeaveRequestId requestId,
        UUID subjectId,
        String cancelReason
) implements DomainEvent {
    public LeaveRequestCancelled(LeaveRequestId requestId, UUID subjectId, String cancelReason) {
        this(UUID.randomUUID(), Instant.now(), requestId, subjectId, cancelReason);
    }
    @Override
    public String eventType() {
        return "leave.request.cancelled";
    }
}
