package tech.kayys.numou.workforce.domain.compliance;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.workforce.domain.compliance.event.ComplianceAssessmentRecorded;
import tech.kayys.numou.workforce.domain.compliance.event.ComplianceViolationDetected;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Aggregate root representing a compliance assessment – a formal evaluation of
 * whether a specific worker meets a specific compliance requirement at a point
 * in time.
 *
 * <p>Status transitions:
 * <pre>
 *   PENDING → COMPLIANT          (markCompliant)
 *   PENDING → NON_COMPLIANT      (markNonCompliant) → raises ComplianceViolationDetected
 *   PENDING → PARTIALLY_COMPLIANT (markPartiallyCompliant)
 *   any     → EXEMPT             (markExempt)
 * </pre>
 */
public class ComplianceAssessment extends AbstractAggregateRoot<ComplianceAssessmentId> {

    private final ComplianceRequirementId requirementId;
    private final WorkerId workerId;
    private final EmploymentId employmentId;
    private ComplianceAssessmentStatus status;
    private final Instant assessedAt;
    private final String assessedBy;
    private String notes;
    /** Nullable – may not always be applicable. */
    private LocalDate nextAssessmentDue;

    // -------------------------------------------------------------------------
    // Constructor (private – use factory)
    // -------------------------------------------------------------------------

    private ComplianceAssessment(
            ComplianceAssessmentId id,
            ComplianceRequirementId requirementId,
            WorkerId workerId,
            EmploymentId employmentId,
            ComplianceAssessmentStatus status,
            Instant assessedAt,
            String assessedBy,
            String notes,
            LocalDate nextAssessmentDue) {

        super(id);
        this.requirementId     = Objects.requireNonNull(requirementId, "requirementId must not be null");
        this.workerId          = Objects.requireNonNull(workerId,       "workerId must not be null");
        this.employmentId      = Objects.requireNonNull(employmentId,   "employmentId must not be null");
        this.status            = Objects.requireNonNull(status,         "status must not be null");
        this.assessedAt        = Objects.requireNonNull(assessedAt,     "assessedAt must not be null");
        this.assessedBy        = Objects.requireNonNull(assessedBy,     "assessedBy must not be null");
        this.notes             = notes; // nullable
        this.nextAssessmentDue = nextAssessmentDue; // nullable
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Create a new pending assessment and raise a
     * {@link ComplianceAssessmentRecorded} domain event.
     */
    public static ComplianceAssessment create(
            ComplianceRequirementId requirementId,
            WorkerId workerId,
            EmploymentId employmentId,
            Instant assessedAt,
            String assessedBy) {

        ComplianceAssessmentId id = ComplianceAssessmentId.generate();

        ComplianceAssessment assessment = new ComplianceAssessment(
                id, requirementId, workerId, employmentId,
                ComplianceAssessmentStatus.PENDING,
                assessedAt, assessedBy, null, null);

        assessment.registerEvent(
                new ComplianceAssessmentRecorded(id, workerId, ComplianceAssessmentStatus.PENDING));
        return assessment;
    }

    /**
     * Reconstitute an existing assessment from persistence (no events raised).
     */
    public static ComplianceAssessment reconstitute(
            ComplianceAssessmentId id,
            ComplianceRequirementId requirementId,
            WorkerId workerId,
            EmploymentId employmentId,
            ComplianceAssessmentStatus status,
            Instant assessedAt,
            String assessedBy,
            String notes,
            LocalDate nextAssessmentDue) {

        return new ComplianceAssessment(
                id, requirementId, workerId, employmentId,
                status, assessedAt, assessedBy, notes, nextAssessmentDue);
    }

    // -------------------------------------------------------------------------
    // Behaviour
    // -------------------------------------------------------------------------

    /**
     * Mark this assessment as COMPLIANT.
     *
     * @param notes           optional assessor notes.
     * @param nextAssessmentDue optional date for the next scheduled check.
     */
    public void markCompliant(String notes, LocalDate nextAssessmentDue) {
        this.status            = ComplianceAssessmentStatus.COMPLIANT;
        this.notes             = notes;
        this.nextAssessmentDue = nextAssessmentDue;
    }

    /**
     * Mark this assessment as NON_COMPLIANT and raise a
     * {@link ComplianceViolationDetected} domain event.
     *
     * @param notes           required assessor notes describing the violation.
     * @param nextAssessmentDue optional date for re-assessment.
     */
    public void markNonCompliant(String notes, LocalDate nextAssessmentDue) {
        Objects.requireNonNull(notes, "notes must not be null when marking non-compliant");
        this.status            = ComplianceAssessmentStatus.NON_COMPLIANT;
        this.notes             = notes;
        this.nextAssessmentDue = nextAssessmentDue;

        registerEvent(new ComplianceViolationDetected(getId(), workerId, requirementId, notes));
    }

    /**
     * Mark this assessment as PARTIALLY_COMPLIANT.
     *
     * @param notes           optional assessor notes.
     * @param nextAssessmentDue optional date for the next scheduled check.
     */
    public void markPartiallyCompliant(String notes, LocalDate nextAssessmentDue) {
        this.status            = ComplianceAssessmentStatus.PARTIALLY_COMPLIANT;
        this.notes             = notes;
        this.nextAssessmentDue = nextAssessmentDue;
    }

    /**
     * Formally exempt a worker from this requirement.
     *
     * @param reason the justification for the exemption (stored in notes).
     */
    public void markExempt(String reason) {
        Objects.requireNonNull(reason, "reason must not be null when marking exempt");
        this.status = ComplianceAssessmentStatus.EXEMPT;
        this.notes  = reason;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public ComplianceRequirementId getRequirementId()    { return requirementId; }
    public WorkerId getWorkerId()                         { return workerId; }
    public EmploymentId getEmploymentId()                 { return employmentId; }
    public ComplianceAssessmentStatus getStatus()        { return status; }
    public Instant getAssessedAt()                       { return assessedAt; }
    public String getAssessedBy()                        { return assessedBy; }
    public String getNotes()                             { return notes; }
    public LocalDate getNextAssessmentDue()              { return nextAssessmentDue; }
}
