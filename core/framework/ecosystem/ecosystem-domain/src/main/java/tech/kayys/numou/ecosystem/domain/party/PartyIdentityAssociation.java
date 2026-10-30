package tech.kayys.numou.ecosystem.domain.party;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Explicit tenant-scoped link between an external authenticated subject and a Party.
 *
 * This association establishes identity linkage only. It does not grant roles,
 * tenant membership, or authorization to access any business resource.
 */
public record PartyIdentityAssociation(
        UUID id,
        TenantId tenantId,
        PartyId partyId,
        IdentitySubjectRef identity,
        PartyIdentityAssociationStatus status,
        Instant linkedAt,
        Instant revokedAt
) {
    public PartyIdentityAssociation {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(partyId, "partyId cannot be null");
        Objects.requireNonNull(identity, "identity cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(linkedAt, "linkedAt cannot be null");
        if (status == PartyIdentityAssociationStatus.ACTIVE && revokedAt != null) {
            throw new IllegalArgumentException("Active association cannot have revokedAt");
        }
        if (status == PartyIdentityAssociationStatus.REVOKED && revokedAt == null) {
            throw new IllegalArgumentException("Revoked association requires revokedAt");
        }
        if (revokedAt != null && revokedAt.isBefore(linkedAt)) {
            throw new IllegalArgumentException("revokedAt cannot be before linkedAt");
        }
    }

    public static PartyIdentityAssociation link(
            UUID id,
            TenantId tenantId,
            PartyId partyId,
            IdentitySubjectRef identity,
            Instant linkedAt) {
        return new PartyIdentityAssociation(id, tenantId, partyId, identity,
                PartyIdentityAssociationStatus.ACTIVE, linkedAt, null);
    }

    public PartyIdentityAssociation revoke(Instant at) {
        Objects.requireNonNull(at, "revocation instant cannot be null");
        if (status == PartyIdentityAssociationStatus.REVOKED) {
            return this;
        }
        if (at.isBefore(linkedAt)) {
            throw new IllegalArgumentException("revocation instant cannot be before linkedAt");
        }
        return new PartyIdentityAssociation(id, tenantId, partyId, identity,
                PartyIdentityAssociationStatus.REVOKED, linkedAt, at);
    }

    public boolean isActive() {
        return status == PartyIdentityAssociationStatus.ACTIVE;
    }
}
