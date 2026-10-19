package tech.kayys.numou.leave.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.leave.domain.LeaveRequestId;

import java.time.Instant;
import java.util.UUID;

public record LeaveRequestApproved(
        UUID eventId,
        Instant occurredAt,
        LeaveRequestId requestId,
        UUID subjectId,
        String approvedBy
) implements DomainEvent {
    public LeaveRequestApproved(LeaveRequestId requestId, UUID subjectId, String approvedBy) {
        this(UUID.randomUUID(), Instant.now(), requestId, subjectId, approvedBy);
    }
    @Override
    public String eventType() {
        return "leave.request.approved";
    }
}
