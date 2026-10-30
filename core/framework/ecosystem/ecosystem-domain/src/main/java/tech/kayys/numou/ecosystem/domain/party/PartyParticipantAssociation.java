package tech.kayys.numou.ecosystem.domain.party;

import tech.kayys.numou.ecosystem.domain.identifier.ParticipantId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Explicit tenant-scoped business relationship between a canonical Party and
 * an Ecosystem Participant. It is distinct from Party identity linkage and
 * Participant-to-Participant relationships.
 *
 * <p>The role classifies the business relationship; it is not an authorization
 * grant, tenant membership, or proof of authentication.
 */
public record PartyParticipantAssociation(
        UUID id, TenantId tenantId, PartyId partyId, ParticipantId participantId,
        PartyParticipantRole role, PartyParticipantAssociationStatus status,
        Instant effectiveFrom, Instant revokedAt) {
    public PartyParticipantAssociation {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(partyId, "partyId cannot be null");
        Objects.requireNonNull(participantId, "participantId cannot be null");
        Objects.requireNonNull(role, "role cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom cannot be null");
        if (status == PartyParticipantAssociationStatus.ACTIVE && revokedAt != null)
            throw new IllegalArgumentException("Active association cannot have revokedAt");
        if (status == PartyParticipantAssociationStatus.REVOKED && revokedAt == null)
            throw new IllegalArgumentException("Revoked association requires revokedAt");
        if (revokedAt != null && revokedAt.isBefore(effectiveFrom))
            throw new IllegalArgumentException("revokedAt cannot be before effectiveFrom");
    }

    public static PartyParticipantAssociation establish(UUID id, TenantId tenantId,
            PartyId partyId, ParticipantId participantId, PartyParticipantRole role, Instant effectiveFrom) {
        return new PartyParticipantAssociation(id, tenantId, partyId, participantId, role,
                PartyParticipantAssociationStatus.ACTIVE, effectiveFrom, null);
    }

    public PartyParticipantAssociation revoke(Instant at) {
        Objects.requireNonNull(at, "revocation instant cannot be null");
        if (status == PartyParticipantAssociationStatus.REVOKED) return this;
        if (at.isBefore(effectiveFrom))
            throw new IllegalArgumentException("revocation instant cannot be before effectiveFrom");
        return new PartyParticipantAssociation(id, tenantId, partyId, participantId, role,
                PartyParticipantAssociationStatus.REVOKED, effectiveFrom, at);
    }

    public boolean isActiveAt(Instant instant) {
        Objects.requireNonNull(instant, "instant cannot be null");
        return status == PartyParticipantAssociationStatus.ACTIVE && !instant.isBefore(effectiveFrom);
    }
}
