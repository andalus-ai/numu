package tech.kayys.numou.ecosystem.domain.organization;

import tech.kayys.numou.ecosystem.domain.event.OrganizationRegistered;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Organization-specific profile and lifecycle attached to a canonical Party identity.
 *
 * <p>The aggregate ID is the existing {@link PartyId}; it deliberately does
 * not create a second organization identity. Party identity, contact points,
 * and addresses remain owned by the Ecosystem Party foundation. This
 * aggregate owns organization-specific classification, names, tenant
 * association, and lifecycle only.
 *
 * <p>The caller must ensure that the referenced Party has kind
 * {@code ORGANIZATION}. The current Party foundation exposes PartyId and
 * PartyKind but does not yet expose a Party aggregate/repository contract
 * that this domain model could validate synchronously.
 */
public final class Organization extends AbstractAggregateRoot<PartyId> {

    private static final long serialVersionUID = 1L;

    private String legalName;
    private String displayName;
    private OrganizationType type;
    private OrganizationStatus status;
    private UUID tenantId;

    private Organization() {
        super();
    }

    private Organization(PartyId partyId) {
        super(partyId);
        this.status = OrganizationStatus.DRAFT;
    }

    /**
     * Creates an organization profile for an already registered Party.
     * Tenant ownership is optional so external organizations can be represented.
     */
    public static Organization register(
            PartyId partyId,
            String legalName,
            String displayName,
            OrganizationType type,
            UUID tenantId) {

        Objects.requireNonNull(partyId, "partyId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        Organization organization = new Organization(partyId);
        organization.legalName = requireText(legalName, "Legal name");
        organization.displayName = requireText(displayName, "Display name");
        organization.type = type;
        organization.tenantId = tenantId;

        organization.raise(OrganizationRegistered.of(
                partyId.value(),
                organization.legalName,
                organization.displayName,
                type,
                tenantId
        ));
        return organization;
    }

    /** Rehydrates the profile without raising a registration event. */
    public static Organization rehydrate(
            PartyId partyId,
            String legalName,
            String displayName,
            OrganizationType type,
            OrganizationStatus status,
            UUID tenantId) {

        Objects.requireNonNull(partyId, "partyId cannot be null");
        Organization organization = new Organization(partyId);
        organization.legalName = requireText(legalName, "Legal name");
        organization.displayName = requireText(displayName, "Display name");
        organization.type = Objects.requireNonNull(type, "type cannot be null");
        organization.status = Objects.requireNonNull(status, "status cannot be null");
        organization.tenantId = tenantId;
        return organization;
    }

    /** Activates a draft or resumes a suspended organization profile. */
    public void activate() {
        if (status == OrganizationStatus.DISSOLVED) {
            throw new InvalidStateException("A dissolved organization cannot be reactivated");
        }
        if (status == OrganizationStatus.ACTIVE) {
            return;
        }
        status = OrganizationStatus.ACTIVE;
        touch();
    }

    /** Suspends an active profile without deleting its identity or history. */
    public void suspend(String reason) {
        if (status != OrganizationStatus.ACTIVE) {
            throw new InvalidStateException(
                    "Only an active organization can be suspended, was " + status
            );
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleViolation("A suspension reason is required");
        }
        status = OrganizationStatus.SUSPENDED;
        touch();
    }

    /** Permanently dissolves this organization profile; the Party identity remains. */
    public void dissolve(String reason) {
        if (status == OrganizationStatus.DISSOLVED) {
            throw new InvalidStateException("Organization is already dissolved");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleViolation("A dissolution reason is required");
        }
        status = OrganizationStatus.DISSOLVED;
        touch();
    }

    public void changeLegalName(String newLegalName) {
        ensureNotDissolved();
        legalName = requireText(newLegalName, "Legal name");
        touch();
    }

    public void changeDisplayName(String newDisplayName) {
        ensureNotDissolved();
        displayName = requireText(newDisplayName, "Display name");
        touch();
    }

    public void reclassify(OrganizationType newType) {
        ensureNotDissolved();
        type = Objects.requireNonNull(newType, "newType cannot be null");
        touch();
    }

    public PartyId partyId() {
        return getId();
    }

    public String legalName() {
        return legalName;
    }

    public String displayName() {
        return displayName;
    }

    public OrganizationType type() {
        return type;
    }

    public OrganizationStatus status() {
        return status;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public boolean isActive() {
        return status == OrganizationStatus.ACTIVE;
    }

    private void ensureNotDissolved() {
        if (status == OrganizationStatus.DISSOLVED) {
            throw new InvalidStateException("A dissolved organization cannot be modified");
        }
    }

    private void touch() {
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolation(label + " is required");
        }
        return value.trim();
    }
}
