package tech.kayys.numou.accounting.domain.document;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

public record DocumentPublished(
        DocumentId documentId,
        Instant occurredAt,
        DocumentVersionId versionId
) implements DomainEvent {
    @Override
    public UUID eventId() {
        return UUID.nameUUIDFromBytes(
                (documentId.value() + ":published:" + versionId.value()).getBytes(StandardCharsets.UTF_8)
        );
    }

    @Override
    public String eventType() {
        return "document.published";
    }
}
