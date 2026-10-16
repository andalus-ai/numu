package tech.kayys.syirkah.kiosk.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/**
 * Kiosk session identifier for customer sessions.
 */
public record KioskSessionId(UUID value) implements DomainId<UUID>, Serializable {

    public KioskSessionId {
        Objects.requireNonNull(value, "KioskSessionId value cannot be null");
    }

    public static KioskSessionId of(UUID value) {
        return new KioskSessionId(value);
    }

    public static KioskSessionId generate() {
        return new KioskSessionId(UUID.randomUUID());
    }

    public static KioskSessionId fromString(String value) {
        return new KioskSessionId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "KioskSessionId{" + value + "}";
    }
}