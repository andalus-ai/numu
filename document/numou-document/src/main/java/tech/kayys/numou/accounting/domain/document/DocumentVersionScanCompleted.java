package tech.kayys.numou.accounting.domain.document;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/** Published after the authoritative scan decision for one immutable version is persisted. */
public record DocumentVersionScanCompleted(
        DocumentId documentId,
        DocumentVersionId versionId,
        DocumentScanStatus status,
        String scanner,
        String signatureVersion,
        String failureCode,
        Instant occurredAt
) implements DomainEvent {
    public DocumentVersionScanCompleted {
        if (documentId == null || versionId == null || status == null || occurredAt == null) {
            throw new IllegalArgumentException("Scan event identity, status and time are required");
        }
        if (status == DocumentScanStatus.PENDING) {
            throw new IllegalArgumentException("A scan completion event cannot be PENDING");
        }
        if (scanner == null || scanner.isBlank()) {
            throw new IllegalArgumentException("scanner must not be blank");
        }
        if (status == DocumentScanStatus.FAILED && (failureCode == null || failureCode.isBlank())) {
            throw new IllegalArgumentException("FAILED scan events require failureCode");
        }
        if (status != DocumentScanStatus.FAILED && failureCode != null) {
            throw new IllegalArgumentException("Only FAILED scan events may contain failureCode");
        }
    }

    @Override
    public UUID eventId() {
        var identity = documentId.value() + ":" + versionId.value() + ":" + status + ":" + occurredAt;
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String eventType() {
        return "document.version-scan-completed";
    }
}
