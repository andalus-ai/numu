package tech.kayys.syirkah.ecosystem.domain.party;

/**
 * Directional relationship type between parties (config02.md §P4-11 #17).
 */
public enum PartyRelationshipType {
    CUSTOMER_OF,
    SUPPLIER_OF,
    PARTNER_OF,
    PROVIDER_OF,
    MEMBER_OF,
    SUBSIDIARY_OF
}
