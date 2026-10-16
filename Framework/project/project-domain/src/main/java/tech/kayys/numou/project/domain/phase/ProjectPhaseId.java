package tech.kayys.syirkah.project.domain.phase;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/**
 * Stable identity of a project phase.
 *
 * A phase is its own aggregate root - it is deliberately not an
 * entity inside the {@code Project} aggregate, because a real project
 * can hold many phases and loading them all with the Project would
 * make the Project aggregate grow without bound.
 */
public record ProjectPhaseId(UUID value)
        implements DomainId<UUID> {

    public ProjectPhaseId {
        Objects.requireNonNull(
                value,
                "Phase id cannot be null"
        );
    }

    public static ProjectPhaseId generate() {
        return new ProjectPhaseId(UUID.randomUUID());
    }

    public static ProjectPhaseId of(UUID value) {
        return new ProjectPhaseId(value);
    }
}
