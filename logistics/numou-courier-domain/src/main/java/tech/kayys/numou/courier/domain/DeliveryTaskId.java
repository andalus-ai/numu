package tech.kayys.numou.courier.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Stable identity for a courier's delivery assignment. */
public record DeliveryTaskId(UUID value) implements DomainId<UUID>, Serializable {
    public DeliveryTaskId { Objects.requireNonNull(value, "DeliveryTaskId value must not be null"); }
    public static DeliveryTaskId of(UUID value) { return new DeliveryTaskId(value); }
    public static DeliveryTaskId of(String value) { return new DeliveryTaskId(UUID.fromString(value)); }
    public static DeliveryTaskId generate() { return new DeliveryTaskId(UUID.randomUUID()); }
    @Override public String toString() { return value.toString(); }
}
