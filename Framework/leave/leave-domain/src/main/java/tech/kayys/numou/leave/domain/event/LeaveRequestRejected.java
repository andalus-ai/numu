package tech.kayys.numou.leave.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.leave.domain.LeaveRequestId;

import java.time.Instant;
import java.util.UUID;

public record LeaveRequestRejected(
        UUID eventId,
        Instant occurredAt,
        LeaveRequestId requestId,
        UUID subjectId,
        String rejectedBy,
        String reason
) implements DomainEvent {
    public LeaveRequestRejected(LeaveRequestId requestId, UUID subjectId, String rejectedBy, String reason) {
        this(UUID.randomUUID(), Instant.now(), requestId, subjectId, rejectedBy, reason);
    }
    @Override
    public String eventType() {
        return "leave.request.rejected";
    }
}
