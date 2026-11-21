package tech.kayys.numou.socialgraph.spi;

import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.socialgraph.domain.SocialRelationshipId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Append-only actor-attributed directed relationship command audit. */
public record SocialRelationshipAuditRecord(UUID auditId, TenantId tenantId,
        SocialRelationshipId relationshipId, PartyId actorPartyId, String action,
        String idempotencyKey, Instant occurredAt) {
    public SocialRelationshipAuditRecord {
        Objects.requireNonNull(auditId, "auditId cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(relationshipId, "relationshipId cannot be null");
        Objects.requireNonNull(actorPartyId, "actorPartyId cannot be null");
        if (action == null || action.isBlank() || action.length() > 48)
            throw new IllegalArgumentException("Invalid audit action");
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128)
            throw new IllegalArgumentException("Invalid idempotency key");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
    }
}
