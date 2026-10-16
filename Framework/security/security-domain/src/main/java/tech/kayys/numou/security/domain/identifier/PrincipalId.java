package tech.kayys.syirkah.security.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/** Identifies an authenticated principal: a user account or a service. */
public record PrincipalId(UUID value) implements DomainId<UUID>, Serializable {

    public PrincipalId {
        Objects.requireNonNull(value, "PrincipalId value cannot be null");
    }

    public static PrincipalId of(UUID value) {
        return new PrincipalId(value);
    }

    public static PrincipalId generate() {
        return new PrincipalId(UUID.randomUUID());
    }

    public static PrincipalId fromString(String value) {
        return new PrincipalId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "PrincipalId{" + value + "}";
    }
}
