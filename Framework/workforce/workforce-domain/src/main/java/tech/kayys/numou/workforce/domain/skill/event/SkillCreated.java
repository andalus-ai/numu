package tech.kayys.numou.workforce.domain.skill.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.skill.SkillCategoryRef;
import tech.kayys.numou.workforce.domain.skill.SkillId;

import java.time.Instant;
import java.util.UUID;

public record SkillCreated(
        UUID eventId,
        Instant occurredAt,
        SkillId skillId,
        String code,
        String name,
        SkillCategoryRef category
) implements DomainEvent {

    public SkillCreated(SkillId skillId, String code, String name, SkillCategoryRef category) {
        this(UUID.randomUUID(), Instant.now(), skillId, code, name, category);
    }

    @Override
    public String eventType() {
        return "workforce.skill.created";
    }
}
