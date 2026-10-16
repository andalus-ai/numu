package tech.kayys.syirkah.accounting.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record AccountId(UUID value) implements DomainId<UUID> {
    public AccountId {
        Objects.requireNonNull(value, "AccountId value cannot be null");
    }
    public UUID getValue() { return value; }
    public static AccountId generate() {
        return new AccountId(UUID.randomUUID());
    }
    public static AccountId of(UUID value) {
        return new AccountId(value);
    }
    public static AccountId of(String value) {
        return new AccountId(UUID.fromString(value));
    }
    public static AccountId fromString(String value) {
        return new AccountId(UUID.fromString(value));
    }
}
