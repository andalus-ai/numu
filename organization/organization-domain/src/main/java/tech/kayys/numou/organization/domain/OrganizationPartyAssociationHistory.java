package tech.kayys.numou.organization.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable audit entry for one association lifecycle transition. */
public record OrganizationPartyAssociationHistory(
        UUID eventId,
        UUID associationId,
        String eventType,
        OrganizationPartyAssociationStatus fromStatus,
        OrganizationPartyAssociationStatus toStatus,
        Instant occurredAt,
        String actorId,
        String reason) {
    public OrganizationPartyAssociationHistory {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(associationId, "associationId");
        if (eventType == null || eventType.isBlank()) throw new IllegalArgumentException("eventType is required");
        Objects.requireNonNull(toStatus, "toStatus");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (actorId == null || actorId.isBlank()) throw new IllegalArgumentException("actorId is required");
        eventType = eventType.trim();
        actorId = actorId.trim();
        reason = reason == null || reason.isBlank() ? null : reason.trim();
    }
}
