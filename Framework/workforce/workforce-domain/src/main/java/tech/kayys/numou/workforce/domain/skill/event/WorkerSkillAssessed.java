package tech.kayys.numou.workforce.domain.skill.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.skill.ProficiencyLevel;
import tech.kayys.numou.workforce.domain.skill.SkillId;
import tech.kayys.numou.workforce.domain.skill.WorkerSkillId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WorkerSkillAssessed(
        UUID eventId,
        Instant occurredAt,
        WorkerSkillId workerSkillId,
        WorkerId workerId,
        SkillId skillId,
        ProficiencyLevel previousProficiency,
        ProficiencyLevel newProficiency,
        LocalDate assessedOn
) implements DomainEvent {

    public WorkerSkillAssessed(WorkerSkillId workerSkillId, WorkerId workerId, SkillId skillId,
                                ProficiencyLevel previousProficiency, ProficiencyLevel newProficiency,
                                LocalDate assessedOn) {
        this(UUID.randomUUID(), Instant.now(), workerSkillId, workerId, skillId,
                previousProficiency, newProficiency, assessedOn);
    }

    @Override
    public String eventType() {
        return "workforce.worker-skill.assessed";
    }
}
