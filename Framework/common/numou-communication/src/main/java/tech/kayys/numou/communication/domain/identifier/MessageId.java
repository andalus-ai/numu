package tech.kayys.numou.communication.domain.identifier;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/**
 * Message identifier.
 */
public record MessageId(UUID value) implements DomainId<UUID>, Serializable {

    public MessageId {
        Objects.requireNonNull(value, "MessageId value cannot be null");
    }

    public static MessageId of(UUID value) {
        return new MessageId(value);
    }

    public static MessageId generate() {
        return new MessageId(UUID.randomUUID());
    }

    public static MessageId fromString(String value) {
        return new MessageId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "MessageId{" + value + "}";
    }
}
