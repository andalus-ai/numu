package tech.kayys.syirkah.project.domain.commercial;

/**
 * The role a party plays in a contract. A project may hold several
 * parties - customer, main contractor, subcontractor, logistics
 * provider, consultant - all as {@link ContractParty} references.
 */
public enum ContractPartyRole {

    CUSTOMER,

    CLIENT,

    SUPPLIER,

    SUBCONTRACTOR,

    SERVICE_PROVIDER,

    PARTNER,

    OWNER,

    MAIN_CONTRACTOR,

    OTHER
}