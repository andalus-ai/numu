package tech.kayys.numou.accounting.domain.document;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

public record DocumentDeleted(DocumentId documentId, Instant occurredAt) implements DomainEvent {
    @Override
    public UUID eventId() {
        return UUID.nameUUIDFromBytes((documentId.value() + ":deleted").getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String eventType() {
        return "document.deleted";
    }
}
