package tech.kayys.numou.workforce.domain.skill;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.workforce.domain.skill.event.WorkerSkillAdded;
import tech.kayys.numou.workforce.domain.skill.event.WorkerSkillAssessed;
import tech.kayys.numou.workforce.domain.skill.event.WorkerSkillRemoved;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * WorkerSkill aggregate — links a {@link tech.kayys.numou.workforce.domain.worker.Worker}
 * to a catalogued {@link Skill} with a proficiency assessment.
 */
public final class WorkerSkill extends AbstractAggregateRoot<WorkerSkillId> {

    private final WorkerId workerId;
    private final SkillId skillId;
    private ProficiencyLevel proficiency;
    private LocalDate acquiredDate;
    private LocalDate lastAssessedDate;
    private WorkerSkillStatus status;

    private WorkerSkill(WorkerSkillId id, WorkerId workerId, SkillId skillId,
                        ProficiencyLevel proficiency, LocalDate acquiredDate) {
        super(id);
        this.workerId = Objects.requireNonNull(workerId, "workerId must not be null");
        this.skillId = Objects.requireNonNull(skillId, "skillId must not be null");
        this.proficiency = Objects.requireNonNull(proficiency, "proficiency must not be null");
        this.acquiredDate = Objects.requireNonNull(acquiredDate, "acquiredDate must not be null");
        this.lastAssessedDate = acquiredDate;
        this.status = WorkerSkillStatus.ACTIVE;
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Records a new skill for a worker.
     *
     * @param id           unique identity
     * @param workerId     the worker being assessed
     * @param skillId      the skill being recorded
     * @param proficiency  initial proficiency level
     * @param acquiredDate date the skill was acquired / first assessed
     * @return new WorkerSkill with {@link WorkerSkillAdded} event raised
     */
    public static WorkerSkill create(WorkerSkillId id, WorkerId workerId, SkillId skillId,
                                     ProficiencyLevel proficiency, LocalDate acquiredDate) {
        WorkerSkill ws = new WorkerSkill(id, workerId, skillId, proficiency, acquiredDate);
        ws.raise(new WorkerSkillAdded(id, workerId, skillId, proficiency, acquiredDate));
        return ws;
    }

    // -------------------------------------------------------------------------
    // Behaviour
    // -------------------------------------------------------------------------

    /**
     * Updates the proficiency level following a new assessment.
     *
     * @param newProficiency updated level (must not be null)
     * @param assessedOn     date of the assessment
     */
    public void assess(ProficiencyLevel newProficiency, LocalDate assessedOn) {
        Objects.requireNonNull(newProficiency, "newProficiency must not be null");
        Objects.requireNonNull(assessedOn, "assessedOn must not be null");
        ProficiencyLevel previous = this.proficiency;
        this.proficiency = newProficiency;
        this.lastAssessedDate = assessedOn;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new WorkerSkillAssessed(getId(), workerId, skillId, previous, newProficiency, assessedOn));
    }

    /**
     * Removes (deactivates) the worker-skill record.
     */
    public void remove() {
        if (status == WorkerSkillStatus.INACTIVE) {
            return;
        }
        status = WorkerSkillStatus.INACTIVE;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new WorkerSkillRemoved(getId(), workerId, skillId));
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public WorkerId getWorkerId() { return workerId; }
    public SkillId getSkillId() { return skillId; }
    public ProficiencyLevel getProficiency() { return proficiency; }
    public LocalDate getAcquiredDate() { return acquiredDate; }
    public LocalDate getLastAssessedDate() { return lastAssessedDate; }
    public WorkerSkillStatus getStatus() { return status; }
    public boolean isActive() { return status == WorkerSkillStatus.ACTIVE; }
}
