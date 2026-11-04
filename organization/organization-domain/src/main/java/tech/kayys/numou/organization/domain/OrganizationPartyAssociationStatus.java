package tech.kayys.numou.organization.domain;

/** Lifecycle of an explicit OrganizationId-to-PartyId identity association. */
public enum OrganizationPartyAssociationStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    SUSPENDED,
    SUPERSEDED,
    REVOKED;

    public boolean reservesIdentity() {
        return this == PENDING_VERIFICATION || this == ACTIVE || this == SUSPENDED;
    }

    public boolean isTerminal() {
        return this == SUPERSEDED || this == REVOKED;
    }
}
