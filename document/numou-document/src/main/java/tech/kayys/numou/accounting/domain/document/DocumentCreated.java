package tech.kayys.numou.accounting.domain.document;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

public record DocumentCreated(
        DocumentId documentId,
        Instant occurredAt,
        DocumentVersionId initialVersionId
) implements DomainEvent {
    @Override
    public UUID eventId() {
        return UUID.nameUUIDFromBytes((documentId.value() + ":created").getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String eventType() {
        return "document.created";
    }
}
