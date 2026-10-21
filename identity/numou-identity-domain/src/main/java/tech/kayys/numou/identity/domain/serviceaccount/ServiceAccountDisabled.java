package tech.kayys.numou.identity.domain.serviceaccount;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record ServiceAccountDisabled(
        UUID eventId,
        Instant occurredAt,
        ServiceAccountId serviceAccountId
) implements DomainEvent {

    @Override
    public String eventType() {
        return "identity.service-account.disabled";
    }
}
