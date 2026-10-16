package tech.kayys.numou.workforce.domain.qualification.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.qualification.QualificationId;
import tech.kayys.numou.workforce.domain.qualification.WorkerQualificationId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record QualificationRecorded(
        UUID eventId,
        Instant occurredAt,
        WorkerQualificationId workerQualificationId,
        WorkerId workerId,
        QualificationId qualificationId,
        LocalDate issuedOn,
        LocalDate expiresOn
) implements DomainEvent {

    public QualificationRecorded(WorkerQualificationId workerQualificationId, WorkerId workerId,
                                  QualificationId qualificationId, LocalDate issuedOn,
                                  LocalDate expiresOn) {
        this(UUID.randomUUID(), Instant.now(), workerQualificationId, workerId,
                qualificationId, issuedOn, expiresOn);
    }

    @Override
    public String eventType() {
        return "workforce.worker-qualification.recorded";
    }
}
