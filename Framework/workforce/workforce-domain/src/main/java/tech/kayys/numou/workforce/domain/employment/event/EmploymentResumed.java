package tech.kayys.numou.workforce.domain.employment.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.util.UUID;

public record EmploymentResumed(
        UUID eventId,
        Instant occurredAt,
        EmploymentId employmentId,
        WorkerId workerId
) implements DomainEvent {

    public EmploymentResumed(EmploymentId employmentId, WorkerId workerId) {
        this(UUID.randomUUID(), Instant.now(), employmentId, workerId);
    }

    @Override
    public String eventType() {
        return "workforce.employment.resumed";
    }
}
