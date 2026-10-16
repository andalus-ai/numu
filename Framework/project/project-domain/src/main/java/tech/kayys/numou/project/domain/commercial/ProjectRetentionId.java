package tech.kayys.syirkah.project.domain.commercial;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of a retention holdback. */
public record ProjectRetentionId(UUID value) implements DomainId<UUID> {
    public ProjectRetentionId {
        Objects.requireNonNull(value, "Retention id cannot be null");
    }

    public static ProjectRetentionId generate() {
        return new ProjectRetentionId(UUID.randomUUID());
    }

    public static ProjectRetentionId of(UUID value) {
        return new ProjectRetentionId(value);
    }
}