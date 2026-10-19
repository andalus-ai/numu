package tech.kayys.numou.workforce.domain.skill;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

/**
 * Identity value object for a {@link WorkerSkill} aggregate.
 */
public record WorkerSkillId(UUID value) implements DomainId<UUID> {

    public static WorkerSkillId generate() {
        return new WorkerSkillId(UUID.randomUUID());
    }

    public static WorkerSkillId of(UUID value) {
        return new WorkerSkillId(value);
    }

    public static WorkerSkillId of(String value) {
        return new WorkerSkillId(UUID.fromString(value));
    }
}
