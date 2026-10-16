package tech.kayys.syirkah.construction.domain.hse.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record SafetyProfileCreated(
        UUID eventId,
        Instant occurredAt,
        UUID profileId,
        UUID projectId,
        UUID siteId
) implements DomainEvent {
    @Override public String eventType() { return "construction.safety-profile-created"; }
}
