package tech.kayys.numou.workforce.domain.skill.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.skill.SkillId;

import java.time.Instant;
import java.util.UUID;

public record SkillActivated(
        UUID eventId,
        Instant occurredAt,
        SkillId skillId
) implements DomainEvent {

    public SkillActivated(SkillId skillId) {
        this(UUID.randomUUID(), Instant.now(), skillId);
    }

    @Override
    public String eventType() {
        return "workforce.skill.activated";
    }
}
