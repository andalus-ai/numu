package tech.kayys.numou.ecosystem.domain.party;

/**
 * Ecosystem-level business role of a Party in relation to a Participant.
 * These are relationship classifications, not application permissions.
 */
public enum PartyParticipantRole {
    CUSTOMER,
    PARTNER,
    SUPPLIER,
    SERVICE_PROVIDER,
    MEMBER,
    EMPLOYEE,
    OTHER
}
