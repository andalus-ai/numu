package tech.kayys.syirkah.construction.domain.payment.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record PaymentCertificateApproved(
        UUID eventId,
        Instant occurredAt,
        UUID certificateId,
        UUID contractId
) implements DomainEvent {
    @Override public String eventType() { return "construction.payment-certificate-approved"; }
}
