package tech.kayys.numou.workforce.domain.skill.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.skill.ProficiencyLevel;
import tech.kayys.numou.workforce.domain.skill.SkillId;
import tech.kayys.numou.workforce.domain.skill.WorkerSkillId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WorkerSkillAdded(
        UUID eventId,
        Instant occurredAt,
        WorkerSkillId workerSkillId,
        WorkerId workerId,
        SkillId skillId,
        ProficiencyLevel proficiency,
        LocalDate acquiredDate
) implements DomainEvent {

    public WorkerSkillAdded(WorkerSkillId workerSkillId, WorkerId workerId, SkillId skillId,
                             ProficiencyLevel proficiency, LocalDate acquiredDate) {
        this(UUID.randomUUID(), Instant.now(), workerSkillId, workerId, skillId, proficiency, acquiredDate);
    }

    @Override
    public String eventType() {
        return "workforce.worker-skill.added";
    }
}
