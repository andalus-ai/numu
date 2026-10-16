package tech.kayys.syirkah.kiosk.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/**
 * Kiosk device identifier.
 */
public record KioskId(UUID value) implements DomainId<UUID>, Serializable {

    public KioskId {
        Objects.requireNonNull(value, "KioskId value cannot be null");
    }

    public static KioskId of(UUID value) {
        return new KioskId(value);
    }

    public static KioskId generate() {
        return new KioskId(UUID.randomUUID());
    }

    public static KioskId fromString(String value) {
        return new KioskId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "KioskId{" + value + "}";
    }
}