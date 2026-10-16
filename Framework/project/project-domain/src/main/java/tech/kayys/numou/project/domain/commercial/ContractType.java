package tech.kayys.syirkah.project.domain.commercial;

/**
 * The business nature of a contract, deliberately generic so the same
 * model serves construction, software, logistics and consulting
 * projects.
 */
public enum ContractType {

    CUSTOMER,

    SUPPLIER,

    SUBCONTRACT,

    PARTNERSHIP,

    SERVICE,

    FRAMEWORK,

    INTERNAL
}