package tech.kayys.numou.reliability.failure;

/**
 * What a failure means for the retry logic (base01.md §P1-20).
 *
 * <p>Classifying at the point of failure is what prevents the classic bug:
 * retrying a validation error forever, or giving up on a network timeout.
 */
public enum FailureClassification {

    /** Transient and safe to retry (timeout, connection reset, 429). */
    RETRYABLE,

    /** Safe only if the operation is idempotent (5xx from a broker). */
    RETRYABLE_IDEMPOTENT_ONLY,

    /** Will never succeed - do not retry (validation, permission). */
    PERMANENT,

    /** Business rule refused it - a domain decision, not an infrastructure fault. */
    BUSINESS_REJECTION
}
