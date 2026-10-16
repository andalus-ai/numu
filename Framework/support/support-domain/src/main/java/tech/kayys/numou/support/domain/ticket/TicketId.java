package tech.kayys.numou.support.domain.ticket;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record TicketId(UUID value) implements DomainId<UUID> {

    public TicketId {
        Objects.requireNonNull(value, "value cannot be null");
    }

    public static TicketId generate() {
        return new TicketId(UUID.randomUUID());
    }

    public static TicketId of(UUID value) {
        return new TicketId(value);
    }

    public static TicketId fromString(String value) {
        return new TicketId(UUID.fromString(value));
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
