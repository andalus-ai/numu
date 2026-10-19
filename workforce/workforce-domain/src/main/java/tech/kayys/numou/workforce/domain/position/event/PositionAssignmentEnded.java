package tech.kayys.numou.workforce.domain.position.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.position.PositionAssignmentId;
import tech.kayys.numou.workforce.domain.position.PositionId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PositionAssignmentEnded(
        UUID eventId,
        Instant occurredAt,
        PositionAssignmentId assignmentId,
        WorkerId workerId,
        PositionId positionId,
        LocalDate endDate
) implements DomainEvent {

    public PositionAssignmentEnded(
            PositionAssignmentId assignmentId,
            WorkerId workerId,
            PositionId positionId,
            LocalDate endDate
    ) {
        this(UUID.randomUUID(), Instant.now(), assignmentId, workerId, positionId, endDate);
    }

    @Override
    public String eventType() {
        return "workforce.position-assignment.ended";
    }
}
