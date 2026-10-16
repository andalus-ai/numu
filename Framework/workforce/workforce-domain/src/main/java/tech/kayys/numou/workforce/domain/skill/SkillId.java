package tech.kayys.numou.workforce.domain.skill;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly typed identifier for a Skill definition.
 */
public record SkillId(UUID value) implements DomainId<UUID> {

    public SkillId {
        Objects.requireNonNull(value, "Skill ID must not be null");
    }

    public static SkillId generate() {
        return new SkillId(UUID.randomUUID());
    }

    public static SkillId of(UUID value) {
        return new SkillId(value);
    }
}
