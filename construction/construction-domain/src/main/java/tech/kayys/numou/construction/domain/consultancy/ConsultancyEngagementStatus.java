package tech.kayys.numou.construction.domain.consultancy;

/**
 * Lifecycle of a professional-services engagement. This is deliberately
 * distinct from a construction works contract lifecycle.
 */
public enum ConsultancyEngagementStatus {
    DRAFT,
    PROPOSED,
    ACTIVE,
    COMPLETED,
    CANCELLED
}
