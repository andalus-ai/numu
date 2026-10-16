package tech.kayys.numou.crm.domain.event.territory;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Raised when a territory's definition (description) is changed.
 */
public record TerritoryDefinitionChanged(
        UUID eventId,
        Instant occurredAt,
        UUID territoryId,
        String newDescription,
        long timestampMillis) implements DomainEvent {

    public TerritoryDefinitionChanged {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(territoryId, "territoryId cannot be null");
        Objects.requireNonNull(newDescription, "newDescription cannot be null");
    }

    public static TerritoryDefinitionChanged of(UUID territoryId,
                                                String newDescription,
                                                long timestampMillis) {
        return new TerritoryDefinitionChanged(
                UUID.randomUUID(),
                Instant.now(),
                territoryId,
                newDescription,
                timestampMillis
        );
    }

    @Override
    public String eventType() {
        return "crm.territory.definition-changed";
    }
}