package tech.kayys.numou.accounting.domain.document;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

public record DocumentArchived(
        DocumentId documentId,
        Instant occurredAt
) implements DomainEvent {
    @Override
    public UUID eventId() {
        return UUID.nameUUIDFromBytes((documentId.value() + ":archived").getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String eventType() {
        return "document.archived";
    }
}
