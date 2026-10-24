package tech.kayys.numou.courier.domain;
import tech.kayys.numou.foundation.domain.identifier.DomainId;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
/** Stable identity for a first-mile pickup request. */
public record PickupRequestId(UUID value) implements DomainId<UUID>, Serializable {
 public PickupRequestId { Objects.requireNonNull(value, "value must not be null"); }
 public static PickupRequestId generate(){return new PickupRequestId(UUID.randomUUID());}
 public static PickupRequestId of(UUID value){return new PickupRequestId(value);}
 public static PickupRequestId of(String value){return new PickupRequestId(UUID.fromString(value));}
 @Override public String toString(){return value.toString();}
}