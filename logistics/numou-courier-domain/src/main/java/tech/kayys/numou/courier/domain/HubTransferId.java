package tech.kayys.numou.courier.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Stable identity for a hub-to-hub transfer manifest. */
public record HubTransferId(UUID value) implements DomainId<UUID>, Serializable {
    public HubTransferId { Objects.requireNonNull(value, "HubTransferId value must not be null"); }
    public static HubTransferId of(UUID value) { return new HubTransferId(value); }
    public static HubTransferId of(String value) { return new HubTransferId(UUID.fromString(value)); }
    public static HubTransferId generate() { return new HubTransferId(UUID.randomUUID()); }
    @Override public String toString() { return value.toString(); }
}
