package tech.kayys.numou.crm.domain.identifier;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

public record AccountId(UUID value) implements DomainId<UUID>, Serializable {

    public AccountId {
        Objects.requireNonNull(value, "AccountId value cannot be null");
    }

    public static AccountId of(UUID value) {
        return new AccountId(value);
    }

    public static AccountId generate() {
        return of(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value != null ? value.toString() : "";
    }
}
