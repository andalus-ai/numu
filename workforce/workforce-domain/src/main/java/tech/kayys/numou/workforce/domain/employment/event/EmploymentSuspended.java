package tech.kayys.numou.workforce.domain.employment.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.util.UUID;

public record EmploymentSuspended(
        UUID eventId,
        Instant occurredAt,
        EmploymentId employmentId,
        WorkerId workerId,
        String reason
) implements DomainEvent {

    public EmploymentSuspended(EmploymentId employmentId, WorkerId workerId, String reason) {
        this(UUID.randomUUID(), Instant.now(), employmentId, workerId, reason);
    }

    @Override
    public String eventType() {
        return "workforce.employment.suspended";
    }
}
