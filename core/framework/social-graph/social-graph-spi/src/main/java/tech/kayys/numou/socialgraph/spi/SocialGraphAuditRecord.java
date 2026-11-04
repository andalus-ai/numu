package tech.kayys.numou.socialgraph.spi;

import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.socialgraph.domain.SocialConnectionId;
import tech.kayys.numou.socialgraph.domain.SocialConnectionStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Append-only actor-attributed lifecycle transition record. */
public record SocialGraphAuditRecord(UUID auditId, TenantId tenantId,
        SocialConnectionId connectionId, PartyId actorPartyId,
        SocialConnectionStatus fromStatus, SocialConnectionStatus toStatus,
        String action, String idempotencyKey, Instant occurredAt) {
    public SocialGraphAuditRecord {
        Objects.requireNonNull(auditId, "auditId cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(connectionId, "connectionId cannot be null");
        Objects.requireNonNull(actorPartyId, "actorPartyId cannot be null");
        Objects.requireNonNull(toStatus, "toStatus cannot be null");
        if (action == null || action.isBlank()) throw new IllegalArgumentException("action is required");
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey is required");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
    }
}
