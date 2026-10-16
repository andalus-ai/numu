package tech.kayys.syirkah.communication.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/**
 * Notification identifier.
 */
public record NotificationId(UUID value) implements DomainId<UUID>, Serializable {

    public NotificationId {
        Objects.requireNonNull(value, "NotificationId value cannot be null");
    }

    public static NotificationId of(UUID value) {
        return new NotificationId(value);
    }

    public static NotificationId generate() {
        return new NotificationId(UUID.randomUUID());
    }

    public static NotificationId fromString(String value) {
        return new NotificationId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "NotificationId{" + value + "}";
    }
}
