package tech.kayys.numou.workforce.domain.talent;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.talent.event.SuccessionCandidateNominated;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.util.Objects;

public final class SuccessionCandidate extends AbstractAggregateRoot<SuccessionCandidateId> {

    private final SuccessionPlanId planId;
    private final WorkerId workerId;
    private final EmploymentId employmentId;
    private CandidateStatus status;
    private String notes;

    private SuccessionCandidate(
            SuccessionCandidateId id,
            SuccessionPlanId planId,
            WorkerId workerId,
            EmploymentId employmentId,
            String notes
    ) {
        super(id);
        this.planId = Objects.requireNonNull(planId, "planId must not be null");
        this.workerId = Objects.requireNonNull(workerId, "workerId must not be null");
        this.employmentId = Objects.requireNonNull(employmentId, "employmentId must not be null");
        this.notes = notes;
        this.status = CandidateStatus.PROPOSED;
    }

    public static SuccessionCandidate nominate(
            SuccessionCandidateId id,
            SuccessionPlanId planId,
            WorkerId workerId,
            EmploymentId employmentId,
            String notes
    ) {
        SuccessionCandidate candidate = new SuccessionCandidate(id, planId, workerId, employmentId, notes);
        candidate.raise(new SuccessionCandidateNominated(id, planId, workerId));
        return candidate;
    }

    public void markAssessed() {
        this.status = CandidateStatus.ASSESSED;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public void markReady() {
        this.status = CandidateStatus.READY;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public void markDevelopmentRequired() {
        this.status = CandidateStatus.DEVELOPMENT_REQUIRED;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public void withdraw(String reason) {
        this.status = CandidateStatus.WITHDRAWN;
        this.notes = reason;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public SuccessionPlanId getPlanId() { return planId; }
    public WorkerId getWorkerId() { return workerId; }
    public EmploymentId getEmploymentId() { return employmentId; }
    public CandidateStatus getStatus() { return status; }
    public String getNotes() { return notes; }
}
