package tech.kayys.numou.workforce.domain.compliance;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.workforce.domain.compliance.event.ComplianceEvidenceRecorded;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate root representing a single piece of compliance evidence recorded
 * for a specific worker against a specific compliance requirement.
 *
 * <p>Evidence is immutable once created; corrections are modelled as new evidence
 * records referencing the same requirement.
 */
public class ComplianceEvidence extends AbstractAggregateRoot<ComplianceEvidenceId> {

    private final ComplianceRequirementId requirementId;
    private final WorkerId workerId;
    private final EmploymentId employmentId;
    private final ComplianceEvidenceType type;
    private final String title;
    private final String description;
    /** Optional reference to an external system (e.g. document URL, certificate number). */
    private final String externalReference; // nullable
    private final Instant recordedAt;
    /** Nullable – evidence that does not expire will have this set to null. */
    private final Instant expiresAt;
    private final String recordedBy;

    // -------------------------------------------------------------------------
    // Constructor (private – use factory)
    // -------------------------------------------------------------------------

    private ComplianceEvidence(
            ComplianceEvidenceId id,
            ComplianceRequirementId requirementId,
            WorkerId workerId,
            EmploymentId employmentId,
            ComplianceEvidenceType type,
            String title,
            String description,
            String externalReference,
            Instant recordedAt,
            Instant expiresAt,
            String recordedBy) {

        super(id);
        this.requirementId     = Objects.requireNonNull(requirementId,  "requirementId must not be null");
        this.workerId          = Objects.requireNonNull(workerId,        "workerId must not be null");
        this.employmentId      = Objects.requireNonNull(employmentId,    "employmentId must not be null");
        this.type              = Objects.requireNonNull(type,            "type must not be null");
        this.title             = Objects.requireNonNull(title,           "title must not be null");
        this.description       = Objects.requireNonNull(description,     "description must not be null");
        this.externalReference = externalReference; // nullable
        this.recordedAt        = Objects.requireNonNull(recordedAt,      "recordedAt must not be null");
        this.expiresAt         = expiresAt; // nullable
        this.recordedBy        = Objects.requireNonNull(recordedBy,      "recordedBy must not be null");
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Record a new piece of compliance evidence and raise a
     * {@link ComplianceEvidenceRecorded} domain event.
     */
    public static ComplianceEvidence create(
            ComplianceRequirementId requirementId,
            WorkerId workerId,
            EmploymentId employmentId,
            ComplianceEvidenceType type,
            String title,
            String description,
            String externalReference,
            Instant recordedAt,
            Instant expiresAt,
            String recordedBy) {

        ComplianceEvidenceId id = ComplianceEvidenceId.generate();

        ComplianceEvidence evidence = new ComplianceEvidence(
                id, requirementId, workerId, employmentId,
                type, title, description, externalReference,
                recordedAt, expiresAt, recordedBy);

        evidence.registerEvent(new ComplianceEvidenceRecorded(id, workerId, requirementId));
        return evidence;
    }

    /**
     * Reconstitute existing evidence from persistence (no events raised).
     */
    public static ComplianceEvidence reconstitute(
            ComplianceEvidenceId id,
            ComplianceRequirementId requirementId,
            WorkerId workerId,
            EmploymentId employmentId,
            ComplianceEvidenceType type,
            String title,
            String description,
            String externalReference,
            Instant recordedAt,
            Instant expiresAt,
            String recordedBy) {

        return new ComplianceEvidence(
                id, requirementId, workerId, employmentId,
                type, title, description, externalReference,
                recordedAt, expiresAt, recordedBy);
    }

    // -------------------------------------------------------------------------
    // Behaviour
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if this evidence has an expiry date and that date is
     * strictly before {@code now}.
     *
     * @param now the instant to evaluate against; must not be null.
     */
    public boolean isExpired(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return expiresAt != null && now.isAfter(expiresAt);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public ComplianceRequirementId getRequirementId()  { return requirementId; }
    public WorkerId getWorkerId()                       { return workerId; }
    public EmploymentId getEmploymentId()               { return employmentId; }
    public ComplianceEvidenceType getType()             { return type; }
    public String getTitle()                            { return title; }
    public String getDescription()                      { return description; }
    public String getExternalReference()               { return externalReference; }
    public Instant getRecordedAt()                     { return recordedAt; }
    public Instant getExpiresAt()                      { return expiresAt; }
    public String getRecordedBy()                      { return recordedBy; }
}
