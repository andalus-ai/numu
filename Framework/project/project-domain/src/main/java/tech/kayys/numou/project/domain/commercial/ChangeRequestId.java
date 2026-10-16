package tech.kayys.syirkah.project.domain.commercial;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of a change request. */
public record ChangeRequestId(UUID value) implements DomainId<UUID> {
    public ChangeRequestId {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Change request id cannot be null"
            );
        }
    }

    public static ChangeRequestId generate() {
        return new ChangeRequestId(UUID.randomUUID());
    }

    public static ChangeRequestId of(UUID value) {
        return new ChangeRequestId(value);
    }
}