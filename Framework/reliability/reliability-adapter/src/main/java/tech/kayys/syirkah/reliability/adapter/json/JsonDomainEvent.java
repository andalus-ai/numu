package tech.kayys.syirkah.reliability.adapter.json;

import com.fasterxml.jackson.databind.JsonNode;
import tech.kayys.syirkah.event.domain.BusinessEvent;
import tech.kayys.syirkah.event.domain.valueobject.EventVersion;

import java.time.Instant;
import java.util.UUID;

/**
 * Generic wire payload for any domain event: identity + a JSON {@code data} tree. Lets every bounded context publish
 * through the outbox without a hand-written envelope class per event, while consumers still dispatch on
 * {@code eventType}.
 */
public record JsonDomainEvent(UUID eventId, String occurredAtIso, String eventType, JsonNode data) implements BusinessEvent {

    @Override
    public Instant occurredAt() {
        return Instant.parse(occurredAtIso);
    }

    @Override
    public EventVersion eventVersion() {
        return EventVersion.V1;
    }
}
