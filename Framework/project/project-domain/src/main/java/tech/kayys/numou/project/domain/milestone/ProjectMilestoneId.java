package tech.kayys.syirkah.project.domain.milestone;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/**
 * Stable identity of a project milestone.
 *
 * Like a phase, a milestone is its own aggregate root linked to the
 * project by identifier only.
 */
public record ProjectMilestoneId(UUID value)
        implements DomainId<UUID> {

    public ProjectMilestoneId {
        Objects.requireNonNull(
                value,
                "Milestone id cannot be null"
        );
    }

    public static ProjectMilestoneId generate() {
        return new ProjectMilestoneId(UUID.randomUUID());
    }

    public static ProjectMilestoneId of(UUID value) {
        return new ProjectMilestoneId(value);
    }
}
