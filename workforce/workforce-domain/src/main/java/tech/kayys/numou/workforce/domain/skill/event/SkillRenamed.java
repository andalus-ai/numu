package tech.kayys.numou.workforce.domain.skill.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.skill.SkillId;

import java.time.Instant;
import java.util.UUID;

public record SkillRenamed(
        UUID eventId,
        Instant occurredAt,
        SkillId skillId,
        String oldName,
        String newName
) implements DomainEvent {

    public SkillRenamed(SkillId skillId, String oldName, String newName) {
        this(UUID.randomUUID(), Instant.now(), skillId, oldName, newName);
    }

    @Override
    public String eventType() {
        return "workforce.skill.renamed";
    }
}
