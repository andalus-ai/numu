package tech.kayys.numou.workforce.domain.learning.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.learning.LearningCertificateId;
import tech.kayys.numou.workforce.domain.learning.LearningEnrollmentId;

import java.time.Instant;
import java.util.UUID;

public record LearningCertificateIssued(
        UUID eventId,
        Instant occurredAt,
        LearningCertificateId id,
        LearningEnrollmentId enrollmentId,
        String certificateNumber
) implements DomainEvent {
    public LearningCertificateIssued(LearningCertificateId id, LearningEnrollmentId enrollmentId, String certificateNumber) {
        this(UUID.randomUUID(), Instant.now(), id, enrollmentId, certificateNumber);
    }
    @Override public String eventType() { return "workforce.learning.certificate.issued"; }
}
