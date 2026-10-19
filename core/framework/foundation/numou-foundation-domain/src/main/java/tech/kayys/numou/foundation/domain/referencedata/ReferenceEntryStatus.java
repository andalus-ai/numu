package tech.kayys.numou.foundation.domain.referencedata;

/**
 * Controlled lifecycle of a reference data entry (config03.md §P4-16 #5).
 */
public enum ReferenceEntryStatus {
    DRAFT,
    ACTIVE,
    DEPRECATED,
    RETIRED
}
