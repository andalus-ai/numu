package tech.kayys.syirkah.groceries.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/**
 * Scale device identifier.
 */
public record ScaleId(UUID value) implements DomainId<UUID>, Serializable {

    public ScaleId {
        Objects.requireNonNull(value, "ScaleId value cannot be null");
    }

    public static ScaleId of(UUID value) {
        return new ScaleId(value);
    }

    public static ScaleId generate() {
        return new ScaleId(UUID.randomUUID());
    }

    public static ScaleId fromString(String value) {
        return new ScaleId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "ScaleId{" + value + "}";
    }
}
