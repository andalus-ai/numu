package tech.kayys.syirkah.identity.domain.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.identity.domain.user.UserId;

import java.time.Instant;
import java.util.UUID;

public record UserDeactivated(
        UUID eventId,
        Instant occurredAt,
        UserId userId,
        String reason
) implements DomainEvent {

    @Override
    public String eventType() {
        return "identity.user.deactivated";
    }

}
