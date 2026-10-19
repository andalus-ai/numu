package tech.kayys.numou.workforce.domain.skill;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.workforce.domain.skill.event.SkillActivated;
import tech.kayys.numou.workforce.domain.skill.event.SkillCreated;
import tech.kayys.numou.workforce.domain.skill.event.SkillDeactivated;
import tech.kayys.numou.workforce.domain.skill.event.SkillRenamed;

import java.time.Instant;
import java.util.Objects;

/**
 * Skill aggregate — a catalogued capability that can be assigned to workers.
 *
 * <p>Lifecycle: {@code ACTIVE} ↔ {@code INACTIVE}.
 */
public final class Skill extends AbstractAggregateRoot<SkillId> {

    private String code;
    private String name;
    private String description;
    private SkillCategoryRef category;
    private SkillStatus status;

    private Skill(SkillId id, String code, String name, String description,
                  SkillCategoryRef category) {
        super(id);
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.description = description;
        this.category = category;
        this.status = SkillStatus.ACTIVE;
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Creates and returns a new, active {@link Skill}.
     *
     * @param id          unique identity
     * @param code        short, unique code (e.g. "JAVA", "PMP")
     * @param name        human-readable name
     * @param description optional longer description
     * @param category    optional skill-category reference
     * @return new Skill aggregate with {@link SkillCreated} event raised
     */
    public static Skill create(SkillId id, String code, String name,
                                String description, SkillCategoryRef category) {
        Skill skill = new Skill(id, code, name, description, category);
        skill.raise(new SkillCreated(skill.getId(), code, name, category));
        return skill;
    }

    // -------------------------------------------------------------------------
    // Behaviour
    // -------------------------------------------------------------------------

    /** Activates the skill so it can be assigned to workers. */
    public void activate() {
        if (status == SkillStatus.ACTIVE) {
            return;
        }
        status = SkillStatus.ACTIVE;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new SkillActivated(getId()));
    }

    /** Deactivates the skill; existing worker-skill records are not affected. */
    public void deactivate() {
        if (status == SkillStatus.INACTIVE) {
            return;
        }
        status = SkillStatus.INACTIVE;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new SkillDeactivated(getId()));
    }

    /**
     * Renames the skill.
     *
     * @param newName new human-readable name (must not be blank)
     */
    public void rename(String newName) {
        Objects.requireNonNull(newName, "newName must not be null");
        if (newName.isBlank()) {
            throw new IllegalArgumentException("Skill name must not be blank");
        }
        String old = this.name;
        this.name = newName;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new SkillRenamed(getId(), old, newName));
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public SkillCategoryRef getCategory() { return category; }
    public SkillStatus getStatus() { return status; }
    public boolean isActive() { return status == SkillStatus.ACTIVE; }
}
