package tech.kayys.syirkah.identity.domain.user;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record UserId(UUID value) implements DomainId<UUID> {

    public UserId {
        Objects.requireNonNull(value, "UserId value cannot be null");
    }

    public static UserId newId() {
        return new UserId(UUID.randomUUID());
    }

    public static UserId of(UUID value) {
        return new UserId(value);
    }

}
