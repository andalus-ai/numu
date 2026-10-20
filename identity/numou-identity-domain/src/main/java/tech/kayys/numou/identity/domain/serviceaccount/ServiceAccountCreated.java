package tech.kayys.numou.identity.domain.serviceaccount;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.identity.domain.user.UserId;

import java.time.Instant;
import java.util.UUID;

public record ServiceAccountCreated(
        UUID eventId,
        Instant occurredAt,
        ServiceAccountId serviceAccountId,
        String name,
        UserId ownerUserId
) implements DomainEvent {

    @Override
    public String eventType() {
        return "identity.service-account.created";
    }
}
