package tech.kayys.numou.finance.treasury.domain.identifier;

import tech.kayys.numou.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

public record CashMovementId(UUID value) implements DomainId<UUID>, Serializable {
        public CashMovementId {
        Objects.requireNonNull(value, "CashMovementId value cannot be null");
    }
    public static CashMovementId of(UUID value) { return new CashMovementId(value); }
    public static CashMovementId generate() { return new CashMovementId(UUID.randomUUID()); }

    @Override
    public String toString() {
        return value != null ? value.toString() : "";
    }
}
