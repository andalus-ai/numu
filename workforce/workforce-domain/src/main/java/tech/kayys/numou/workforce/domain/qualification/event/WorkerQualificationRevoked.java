package tech.kayys.numou.workforce.domain.qualification.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.qualification.QualificationId;
import tech.kayys.numou.workforce.domain.qualification.WorkerQualificationId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.util.UUID;

public record WorkerQualificationRevoked(
        UUID eventId,
        Instant occurredAt,
        WorkerQualificationId workerQualificationId,
        WorkerId workerId,
        QualificationId qualificationId
) implements DomainEvent {

    public WorkerQualificationRevoked(WorkerQualificationId workerQualificationId,
                                       WorkerId workerId, QualificationId qualificationId) {
        this(UUID.randomUUID(), Instant.now(), workerQualificationId, workerId, qualificationId);
    }

    @Override
    public String eventType() {
        return "workforce.worker-qualification.revoked";
    }
}
