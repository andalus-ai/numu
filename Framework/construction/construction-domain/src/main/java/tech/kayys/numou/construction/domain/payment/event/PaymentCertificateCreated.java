package tech.kayys.numou.construction.domain.payment.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record PaymentCertificateCreated(
        UUID eventId,
        Instant occurredAt,
        UUID certificateId,
        UUID contractId,
        int certificateNumber
) implements DomainEvent {
    @Override public String eventType() { return "construction.payment-certificate-created"; }
}
