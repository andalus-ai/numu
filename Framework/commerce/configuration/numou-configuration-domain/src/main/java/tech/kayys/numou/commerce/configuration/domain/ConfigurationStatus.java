package tech.kayys.numou.commerce.configuration.domain;

/**
 * Lifecycle of a product configuration (product02.md §19).
 * Validation is derived — not persisted as VALID/INVALID.
 */
public enum ConfigurationStatus {
    DRAFT,
    COMPLETED,
    CANCELLED
}
