package tech.kayys.numou.workforce.domain.qualification.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.qualification.QualificationId;

import java.time.Instant;
import java.util.UUID;

public record QualificationActivated(
        UUID eventId,
        Instant occurredAt,
        QualificationId qualificationId
) implements DomainEvent {

    public QualificationActivated(QualificationId qualificationId) {
        this(UUID.randomUUID(), Instant.now(), qualificationId);
    }

    @Override
    public String eventType() {
        return "workforce.qualification.activated";
    }
}
