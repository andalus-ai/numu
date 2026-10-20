package tech.kayys.numou.identity.domain.token;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record PersonalAccessTokenRevoked(
        UUID eventId,
        Instant occurredAt,
        PersonalAccessTokenId tokenId,
        String principalType,
        UUID principalId
) implements DomainEvent {

    @Override
    public String eventType() {
        return "identity.pat.revoked";
    }
}
