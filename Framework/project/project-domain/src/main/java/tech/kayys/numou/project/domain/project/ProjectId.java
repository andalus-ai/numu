package tech.kayys.syirkah.project.domain.project;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of an accounting project. */
public record ProjectId(UUID value) implements DomainId<UUID> {
    public ProjectId {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Project id cannot be null"
            );
        }
    }

    public static ProjectId generate() {
        return new ProjectId(UUID.randomUUID());
    }

    public static ProjectId of(UUID value) {
        return new ProjectId(value);
    }
}
