package tech.kayys.numou.leave.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.leave.domain.LeaveRequestId;

import java.time.Instant;
import java.util.UUID;

public record LeaveRequestSubmitted(
        UUID eventId,
        Instant occurredAt,
        LeaveRequestId requestId,
        UUID subjectId
) implements DomainEvent {
    public LeaveRequestSubmitted(LeaveRequestId requestId, UUID subjectId) {
        this(UUID.randomUUID(), Instant.now(), requestId, subjectId);
    }
    @Override
    public String eventType() {
        return "leave.request.submitted";
    }
}
