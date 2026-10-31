package tech.kayys.numou.organization.domain;

import tech.kayys.numou.ecosystem.domain.party.PartyId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Explicit, globally unique identity link between an Organization aggregate and a canonical Party.
 *
 * <p>OrganizationId and PartyId remain distinct identifiers. This model owns only the association;
 * it does not own either aggregate, tenant membership, authorization, or Party lifecycle.</p>
 */
public final class OrganizationPartyAssociation {
    private final UUID id;
    private final OrganizationId organizationId;
    private final PartyId partyId;
    private final OrganizationPartyAssociationStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant verifiedAt;
    private final Instant closedAt;
    private final String createdBy;
    private final String updatedBy;
    private final String evidenceReference;
    private final String reason;
    private final long version;

    private OrganizationPartyAssociation(
            UUID id, OrganizationId organizationId, PartyId partyId,
            OrganizationPartyAssociationStatus status, Instant createdAt, Instant updatedAt,
            Instant verifiedAt, Instant closedAt, String createdBy, String updatedBy,
            String evidenceReference, String reason, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.partyId = Objects.requireNonNull(partyId, "partyId");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.createdBy = requireText(createdBy, "createdBy");
        this.updatedBy = requireText(updatedBy, "updatedBy");
        this.evidenceReference = normalizeOptional(evidenceReference);
        this.reason = normalizeOptional(reason);
        this.verifiedAt = verifiedAt;
        this.closedAt = closedAt;
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
        if (status == OrganizationPartyAssociationStatus.PENDING_VERIFICATION && version != 0) {
            throw new IllegalArgumentException("A pending association must be at version zero");
        }
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt precedes createdAt");
        if (verifiedAt != null && verifiedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("verifiedAt precedes createdAt");
        }
        if ((status == OrganizationPartyAssociationStatus.ACTIVE
                || status == OrganizationPartyAssociationStatus.SUSPENDED)
                && verifiedAt == null) {
            throw new IllegalArgumentException("An active or suspended association must be verified");
        }
        if (status.isTerminal() != (closedAt != null)) {
            throw new IllegalArgumentException("Only terminal associations have closedAt");
        }
        if ((status == OrganizationPartyAssociationStatus.SUSPENDED || status.isTerminal())
                && this.reason == null) {
            throw new IllegalArgumentException("Suspended and terminal associations require a reason");
        }
        if (closedAt != null && (!closedAt.equals(updatedAt) || closedAt.isBefore(createdAt))) {
            throw new IllegalArgumentException("closedAt must equal updatedAt and not precede createdAt");
        }
        this.version = version;
    }

    public static OrganizationPartyAssociation propose(
            UUID id, OrganizationId organizationId, PartyId partyId,
            Instant proposedAt, String actorId, String evidenceReference) {
        return new OrganizationPartyAssociation(id, organizationId, partyId,
                OrganizationPartyAssociationStatus.PENDING_VERIFICATION,
                proposedAt, proposedAt, null, null, actorId, actorId,
                evidenceReference, null, 0);
    }

    /** Rehydrates a persisted association without creating a domain transition. */
    public static OrganizationPartyAssociation rehydrate(
            UUID id, OrganizationId organizationId, PartyId partyId,
            OrganizationPartyAssociationStatus status, Instant createdAt, Instant updatedAt,
            Instant verifiedAt, Instant closedAt, String createdBy, String updatedBy,
            String evidenceReference, String reason, long version) {
        return new OrganizationPartyAssociation(id, organizationId, partyId, status,
                createdAt, updatedAt, verifiedAt, closedAt, createdBy, updatedBy,
                evidenceReference, reason, version);
    }

    public OrganizationPartyAssociation activate(Instant at, String actorId) {
        requireStatus(OrganizationPartyAssociationStatus.PENDING_VERIFICATION);
        return transition(OrganizationPartyAssociationStatus.ACTIVE, at, actorId, null, at, null);
    }

    public OrganizationPartyAssociation suspend(Instant at, String actorId, String reason) {
        requireStatus(OrganizationPartyAssociationStatus.ACTIVE);
        return transition(OrganizationPartyAssociationStatus.SUSPENDED, at, actorId,
                requireText(reason, "reason"), verifiedAt, null);
    }

    public OrganizationPartyAssociation reinstate(Instant at, String actorId) {
        requireStatus(OrganizationPartyAssociationStatus.SUSPENDED);
        return transition(OrganizationPartyAssociationStatus.ACTIVE, at, actorId, null, verifiedAt, null);
    }

    public OrganizationPartyAssociation supersede(Instant at, String actorId, String reason) {
        if (status != OrganizationPartyAssociationStatus.ACTIVE
                && status != OrganizationPartyAssociationStatus.SUSPENDED) {
            throw new IllegalStateException("Only active or suspended associations can be superseded");
        }
        return transition(OrganizationPartyAssociationStatus.SUPERSEDED, at, actorId,
                requireText(reason, "reason"), verifiedAt, at);
    }

    public OrganizationPartyAssociation revoke(Instant at, String actorId, String reason) {
        if (!status.reservesIdentity()) {
            throw new IllegalStateException("Only pending, active, or suspended associations can be revoked");
        }
        return transition(OrganizationPartyAssociationStatus.REVOKED, at, actorId,
                requireText(reason, "reason"), verifiedAt, at);
    }

    private OrganizationPartyAssociation transition(
            OrganizationPartyAssociationStatus next, Instant at, String actorId,
            String reason, Instant nextVerifiedAt, Instant nextClosedAt) {
        Objects.requireNonNull(at, "at");
        if (at.isBefore(updatedAt)) throw new IllegalArgumentException("Transition time precedes previous update");
        return new OrganizationPartyAssociation(id, organizationId, partyId, next,
                createdAt, at, nextVerifiedAt, nextClosedAt, createdBy, actorId,
                evidenceReference, reason, version + 1);
    }

    private void requireStatus(OrganizationPartyAssociationStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected " + expected + " but association is " + status);
        }
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID id() { return id; }
    public OrganizationId organizationId() { return organizationId; }
    public PartyId partyId() { return partyId; }
    public OrganizationPartyAssociationStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant verifiedAt() { return verifiedAt; }
    public Instant closedAt() { return closedAt; }
    public String createdBy() { return createdBy; }
    public String updatedBy() { return updatedBy; }
    public String evidenceReference() { return evidenceReference; }
    public String reason() { return reason; }
    public long version() { return version; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof OrganizationPartyAssociation that)) return false;
        return version == that.version && id.equals(that.id)
                && organizationId.equals(that.organizationId) && partyId.equals(that.partyId)
                && status == that.status && createdAt.equals(that.createdAt)
                && updatedAt.equals(that.updatedAt) && Objects.equals(verifiedAt, that.verifiedAt)
                && Objects.equals(closedAt, that.closedAt) && createdBy.equals(that.createdBy)
                && updatedBy.equals(that.updatedBy)
                && Objects.equals(evidenceReference, that.evidenceReference)
                && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, organizationId, partyId, status, createdAt, updatedAt,
                verifiedAt, closedAt, createdBy, updatedBy, evidenceReference, reason, version);
    }
}
