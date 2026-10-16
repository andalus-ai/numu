package tech.kayys.numou.finance.treasury.domain.identifier;

import tech.kayys.numou.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

public record DrawerSessionId(UUID value) implements DomainId<UUID>, Serializable {
        public DrawerSessionId {
        Objects.requireNonNull(value, "DrawerSessionId value cannot be null");
    }
    public static DrawerSessionId of(UUID value) { return new DrawerSessionId(value); }
    public static DrawerSessionId generate() { return new DrawerSessionId(UUID.randomUUID()); }
    public static DrawerSessionId fromString(String val) { return new DrawerSessionId(UUID.fromString(val)); }

    @Override
    public String toString() {
        return value != null ? value.toString() : "";
    }
}
