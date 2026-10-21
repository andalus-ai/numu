package tech.kayys.numou.identity.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.identity.domain.user.UserId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Raised when an administrator or an automated provisioner (SCIM) changes a user's display name.
 */
public record UserRenamed(
        UUID eventId,
        Instant occurredAt,
        UserId userId,
        String previousDisplayName,
        String newDisplayName
) implements DomainEvent {

    public UserRenamed {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(previousDisplayName, "previousDisplayName cannot be null");
        Objects.requireNonNull(newDisplayName, "newDisplayName cannot be null");
    }

    @Override
    public String eventType() {
        return "identity.user-renamed";
    }
}
