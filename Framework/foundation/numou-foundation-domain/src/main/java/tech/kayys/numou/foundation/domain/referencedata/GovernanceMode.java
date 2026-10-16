package tech.kayys.numou.foundation.domain.referencedata;

/**
 * Declared ownership and extension governance for a reference code set (config03.md §P4-16 #7).
 */
public enum GovernanceMode {
    PLATFORM_MANAGED,
    TENANT_EXTENSIBLE,
    TENANT_OVERRIDABLE,
    DOMAIN_OWNED
}
