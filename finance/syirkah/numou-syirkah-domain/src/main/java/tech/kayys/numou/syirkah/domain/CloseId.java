package tech.kayys.numou.numoudomain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Identifier of a period close (tutup buku) snapshot. */
public record CloseId(UUID value) implements DomainId<UUID>, Serializable {
    public CloseId {
        Objects.requireNonNull(value, "CloseId value must not be null");
    }

    public static CloseId generate() {
        return new CloseId(UUID.randomUUID());
    }
}
