package tech.kayys.syirkah.identity.domain.role;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record RoleId(UUID value) implements DomainId<UUID> {
    public RoleId {
        Objects.requireNonNull(value, "value cannot be null");
    }

    public static RoleId generate() {
        return new RoleId(UUID.randomUUID());
    }
}
