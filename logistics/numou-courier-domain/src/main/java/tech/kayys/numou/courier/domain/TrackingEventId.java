package tech.kayys.numou.courier.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Stable identifier for an immutable tracking/custody fact. */
public record TrackingEventId(UUID value) implements DomainId<UUID>, Serializable {
    public TrackingEventId {
        Objects.requireNonNull(value, "TrackingEventId value must not be null");
    }
    public static TrackingEventId of(UUID value) { return new TrackingEventId(value); }
    public static TrackingEventId of(String value) { return new TrackingEventId(UUID.fromString(value)); }
    public static TrackingEventId generate() { return new TrackingEventId(UUID.randomUUID()); }
    @Override public String toString() { return value.toString(); }
}
