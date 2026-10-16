package tech.kayys.syirkah.project.domain.commercial;

/**
 * Claim lifecycle.
 *
 * <pre>
 * DRAFT -&gt; SUBMITTED -&gt; UNDER_REVIEW -&gt; ACCEPTED / PARTIALLY_ACCEPTED / REJECTED
 * ACCEPTED / PARTIALLY_ACCEPTED -&gt; SETTLED
 * </pre>
 */
public enum ClaimStatus {

    DRAFT,

    SUBMITTED,

    UNDER_REVIEW,

    ACCEPTED,

    PARTIALLY_ACCEPTED,

    REJECTED,

    SETTLED,

    WITHDRAWN,

    CANCELLED
}