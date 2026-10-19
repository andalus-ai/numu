package tech.kayys.numou.workforce.domain.worker.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.util.UUID;

public record WorkerDeactivated(
        UUID eventId,
        Instant occurredAt,
        WorkerId workerId,
        String reason
) implements DomainEvent {

    public WorkerDeactivated(WorkerId workerId, String reason) {
        this(UUID.randomUUID(), Instant.now(), workerId, reason);
    }

    @Override
    public String eventType() {
        return "workforce.worker.deactivated";
    }
}
