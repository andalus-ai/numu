package tech.kayys.numou.identity.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.identity.domain.user.UserId;

import java.time.Instant;
import java.util.UUID;

public record UserEmailChanged(
        UUID eventId,
        Instant occurredAt,
        UserId userId,
        String previousEmail,
        String newEmail
) implements DomainEvent {

    @Override
    public String eventType() {
        return "identity.user.email-changed";
    }

}
