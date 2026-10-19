package tech.kayys.numou.workforce.domain.compliance;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.workforce.domain.compliance.event.PolicyAcknowledged;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate root representing a permanent, append-only audit record of a
 * worker acknowledging a policy-type compliance requirement.
 *
 * <p>By design, {@code PolicyAcknowledgement} has <strong>no update methods</strong>.
 * Acknowledgements are immutable facts: if a worker must re-acknowledge a newer
 * policy version, a new {@code PolicyAcknowledgement} aggregate is created for
 * that version.
 */
public class PolicyAcknowledgement extends AbstractAggregateRoot<PolicyAcknowledgementId> {

    private final ComplianceRequirementId requirementId;
    private final WorkerId workerId;
    private final EmploymentId employmentId;
    private final Instant acknowledgedAt;
    /** The version string of the policy document that was acknowledged. */
    private final String acknowledgedVersion;
    /** Optional – the IP address of the client at time of acknowledgement. */
    private final String ipAddress; // nullable

    // -------------------------------------------------------------------------
    // Constructor (private – use factory)
    // -------------------------------------------------------------------------

    private PolicyAcknowledgement(
            PolicyAcknowledgementId id,
            ComplianceRequirementId requirementId,
            WorkerId workerId,
            EmploymentId employmentId,
            Instant acknowledgedAt,
            String acknowledgedVersion,
            String ipAddress) {

        super(id);
        this.requirementId       = Objects.requireNonNull(requirementId,       "requirementId must not be null");
        this.workerId            = Objects.requireNonNull(workerId,             "workerId must not be null");
        this.employmentId        = Objects.requireNonNull(employmentId,         "employmentId must not be null");
        this.acknowledgedAt      = Objects.requireNonNull(acknowledgedAt,       "acknowledgedAt must not be null");
        this.acknowledgedVersion = Objects.requireNonNull(acknowledgedVersion,  "acknowledgedVersion must not be null");
        this.ipAddress           = ipAddress; // nullable
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Record a policy acknowledgement and raise a {@link PolicyAcknowledged}
     * domain event.
     *
     * @param requirementId       the policy requirement being acknowledged
     * @param workerId            the worker making the acknowledgement
     * @param employmentId        the employment context of the worker
     * @param acknowledgedAt      the precise moment of acknowledgement
     * @param acknowledgedVersion the version of the policy document acknowledged
     * @param ipAddress           optional IP address of the acknowledging client
     * @return the newly created immutable {@code PolicyAcknowledgement}
     */
    public static PolicyAcknowledgement create(
            ComplianceRequirementId requirementId,
            WorkerId workerId,
            EmploymentId employmentId,
            Instant acknowledgedAt,
            String acknowledgedVersion,
            String ipAddress) {

        PolicyAcknowledgementId id = PolicyAcknowledgementId.generate();

        PolicyAcknowledgement acknowledgement = new PolicyAcknowledgement(
                id, requirementId, workerId, employmentId,
                acknowledgedAt, acknowledgedVersion, ipAddress);

        acknowledgement.registerEvent(
                new PolicyAcknowledged(id, workerId, requirementId, acknowledgedAt));

        return acknowledgement;
    }

    /**
     * Reconstitute an existing acknowledgement from persistence (no events raised).
     */
    public static PolicyAcknowledgement reconstitute(
            PolicyAcknowledgementId id,
            ComplianceRequirementId requirementId,
            WorkerId workerId,
            EmploymentId employmentId,
            Instant acknowledgedAt,
            String acknowledgedVersion,
            String ipAddress) {

        return new PolicyAcknowledgement(
                id, requirementId, workerId, employmentId,
                acknowledgedAt, acknowledgedVersion, ipAddress);
    }

    // -------------------------------------------------------------------------
    // No update methods – acknowledgements are permanent facts
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public ComplianceRequirementId getRequirementId()  { return requirementId; }
    public WorkerId getWorkerId()                       { return workerId; }
    public EmploymentId getEmploymentId()               { return employmentId; }
    public Instant getAcknowledgedAt()                 { return acknowledgedAt; }
    public String getAcknowledgedVersion()             { return acknowledgedVersion; }
    public String getIpAddress()                       { return ipAddress; }
}
