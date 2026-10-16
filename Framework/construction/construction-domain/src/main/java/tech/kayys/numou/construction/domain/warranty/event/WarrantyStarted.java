package tech.kayys.syirkah.construction.domain.warranty.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WarrantyStarted(
        UUID eventId,
        Instant occurredAt,
        UUID warrantyId,
        UUID projectId,
        LocalDate validUntil
) implements DomainEvent {
    @Override public String eventType() { return "construction.warranty-started"; }
}
