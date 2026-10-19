package tech.kayys.numou.crm.domain.event.territory;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Raised when a territory assignment is ended.
 */
public record TerritoryAssignmentEnded(
        UUID eventId,
        Instant occurredAt,
        UUID assignmentId,
        UUID territoryId,
        UUID accountId,
        String origin,
        UUID assignedByUserId,
        long assignedAtMillis,
        long endedAtMillis) implements DomainEvent {

    public TerritoryAssignmentEnded {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(assignmentId, "assignmentId cannot be null");
        Objects.requireNonNull(territoryId, "territoryId cannot be null");
        Objects.requireNonNull(accountId, "accountId cannot be null");
        Objects.requireNonNull(origin, "origin cannot be null");
        Objects.requireNonNull(assignedByUserId, "assignedByUserId cannot be null");
    }

    public static TerritoryAssignmentEnded of(UUID assignmentId,
                                              UUID territoryId,
                                              UUID accountId,
                                              String origin,
                                              UUID assignedByUserId,
                                              long assignedAtMillis,
                                              long endedAtMillis) {
        return new TerritoryAssignmentEnded(
                UUID.randomUUID(),
                Instant.now(),
                assignmentId,
                territoryId,
                accountId,
                origin,
                assignedByUserId,
                assignedAtMillis,
                endedAtMillis
        );
    }

    @Override
    public String eventType() {
        return "crm.territory.assignment.ended";
    }
}