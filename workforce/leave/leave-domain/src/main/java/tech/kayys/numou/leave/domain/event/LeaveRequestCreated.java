package tech.kayys.numou.leave.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.leave.domain.LeaveRequestId;
import tech.kayys.numou.leave.domain.LeaveTypeId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LeaveRequestCreated(
        UUID eventId,
        Instant occurredAt,
        LeaveRequestId requestId,
        UUID subjectId,
        UUID contextId,
        LeaveTypeId leaveTypeId,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal requestedAmount
) implements DomainEvent {
    public LeaveRequestCreated(LeaveRequestId requestId, UUID subjectId, UUID contextId,
                               LeaveTypeId leaveTypeId, LocalDate startDate,
                               LocalDate endDate, BigDecimal requestedAmount) {
        this(UUID.randomUUID(), Instant.now(), requestId, subjectId, contextId, leaveTypeId, startDate, endDate, requestedAmount);
    }
    @Override
    public String eventType() {
        return "leave.request.created";
    }
}
