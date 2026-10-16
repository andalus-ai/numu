package tech.kayys.syirkah.project.domain.commercial;

/**
 * Contract lifecycle.
 *
 * <pre>
 * DRAFT -&gt; UNDER_REVIEW -&gt; APPROVED -&gt; ACTIVE -&gt; COMPLETED
 *                             ACTIVE &lt;-&gt; SUSPENDED
 *                             ACTIVE/SUSPENDED -&gt; TERMINATED
 * DRAFT/UNDER_REVIEW -&gt; CANCELLED
 * </pre>
 */
public enum ContractStatus {

    DRAFT,

    UNDER_REVIEW,

    APPROVED,

    ACTIVE,

    SUSPENDED,

    COMPLETED,

    TERMINATED,

    CANCELLED
}