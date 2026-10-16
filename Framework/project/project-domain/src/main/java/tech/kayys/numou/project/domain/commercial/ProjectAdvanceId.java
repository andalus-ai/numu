package tech.kayys.syirkah.project.domain.commercial;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of a contract advance. */
public record ProjectAdvanceId(UUID value) implements DomainId<UUID> {
    public ProjectAdvanceId {
        Objects.requireNonNull(value, "Advance id cannot be null");
    }

    public static ProjectAdvanceId generate() {
        return new ProjectAdvanceId(UUID.randomUUID());
    }

    public static ProjectAdvanceId of(UUID value) {
        return new ProjectAdvanceId(value);
    }
}