package tech.kayys.numou.restaurant.kernel.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public record TicketId(UUID value) implements DomainId<UUID>, Serializable {

    public TicketId {
        Objects.requireNonNull(value, "TicketId value must not be null");
    }

    public static TicketId of(UUID value) {
        return new TicketId(value);
    }

    public static TicketId of(String value) {
        return new TicketId(UUID.fromString(value));
    }

    /**
     * Deterministic id for "the ticket of this order's course at this station". Firing the same course twice
     * (a redelivered event, a double tap) targets the same id, so the second create collides instead of
     * producing a duplicate ticket on the KDS.
     */
    public static TicketId forOrderCourseStation(OrderId orderId, int course, StationId stationId) {
        return new TicketId(UUID.nameUUIDFromBytes(
                ("ticket:" + orderId.value() + ":" + course + ":" + stationId.value()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    public static TicketId generate() {
        return new TicketId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
