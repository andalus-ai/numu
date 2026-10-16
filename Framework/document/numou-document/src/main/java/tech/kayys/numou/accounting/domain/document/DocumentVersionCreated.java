package tech.kayys.syirkah.accounting.domain.document;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

public record DocumentVersionCreated(
        DocumentId documentId,
        Instant occurredAt,
        DocumentVersionId versionId,
        int versionNumber
) implements DomainEvent {
    @Override
    public UUID eventId() {
        return UUID.nameUUIDFromBytes(
                (documentId.value() + ":version:" + versionNumber).getBytes(StandardCharsets.UTF_8)
        );
    }

    @Override
    public String eventType() {
        return "document.version-created";
    }
}
