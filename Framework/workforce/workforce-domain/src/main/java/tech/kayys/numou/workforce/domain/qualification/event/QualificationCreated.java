package tech.kayys.numou.workforce.domain.qualification.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.qualification.QualificationId;

import java.time.Instant;
import java.util.UUID;

public record QualificationCreated(
        UUID eventId,
        Instant occurredAt,
        QualificationId qualificationId,
        String code,
        String name,
        String issuingAuthority
) implements DomainEvent {

    public QualificationCreated(QualificationId qualificationId, String code,
                                 String name, String issuingAuthority) {
        this(UUID.randomUUID(), Instant.now(), qualificationId, code, name, issuingAuthority);
    }

    @Override
    public String eventType() {
        return "workforce.qualification.created";
    }
}
