package tech.kayys.numou.syirkah.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Identifier of a syirkah (partnership) aggregate. */
public record SyirkahId(UUID value) implements DomainId<UUID>, Serializable {
    public SyirkahId {
        Objects.requireNonNull(value, "SyirkahId value must not be null");
    }

    public static SyirkahId generate() {
        return new SyirkahId(UUID.randomUUID());
    }

    public static SyirkahId of(String value) {
        return new SyirkahId(UUID.fromString(value));
    }
}
