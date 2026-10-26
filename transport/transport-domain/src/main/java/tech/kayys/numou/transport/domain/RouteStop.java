package tech.kayys.numou.transport.domain;

import java.util.Objects;
import java.util.UUID;

/** A stop's role and order within one route; the actual place is owned by a location capability. */
public record RouteStop(UUID stopId, int sequence, boolean pickupAllowed, boolean dropoffAllowed) {
    public RouteStop {
        Objects.requireNonNull(stopId, "stopId");
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        if (!pickupAllowed && !dropoffAllowed) {
            throw new IllegalArgumentException("a route stop must allow pickup, dropoff, or both");
        }
    }
}
