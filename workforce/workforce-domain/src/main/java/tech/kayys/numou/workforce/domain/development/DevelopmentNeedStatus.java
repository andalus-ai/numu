package tech.kayys.numou.workforce.domain.development;

/**
 * Lifecycle states of a {@link DevelopmentNeed}.
 */
public enum DevelopmentNeedStatus {
    /** Need identified but not yet being worked on. */
    OPEN,
    /** Development activities are underway to address the need. */
    IN_PROGRESS,
    /** The need has been fully addressed. */
    ADDRESSED,
    /** The need was cancelled before being addressed. */
    CANCELLED
}
