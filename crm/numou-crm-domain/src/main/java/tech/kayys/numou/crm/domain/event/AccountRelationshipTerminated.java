package tech.kayys.numou.crm.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Raised when an account relationship is terminated.
 */
public record AccountRelationshipTerminated(
        UUID eventId,
        Instant occurredAt,
        UUID relationshipId,
        UUID sourceAccountId,
        UUID targetAccountId,
        String relationshipType,
        long timestampMillis) implements DomainEvent {

    public AccountRelationshipTerminated {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(relationshipId, "relationshipId cannot be null");
        Objects.requireNonNull(sourceAccountId, "sourceAccountId cannot be null");
        Objects.requireNonNull(targetAccountId, "targetAccountId cannot be null");
        Objects.requireNonNull(relationshipType, "relationshipType cannot be null");
    }

    public static AccountRelationshipTerminated of(UUID relationshipId,
                                                   UUID sourceAccountId,
                                                   UUID targetAccountId,
                                                   String relationshipType,
                                                   long timestampMillis) {
        return new AccountRelationshipTerminated(
                UUID.randomUUID(),
                Instant.now(),
                relationshipId,
                sourceAccountId,
                targetAccountId,
                relationshipType,
                timestampMillis
        );
    }

    @Override
    public String eventType() {
        return "crm.account.relationship.terminated";
    }
}