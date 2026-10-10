package tech.kayys.syirkah.reservation.domain;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifier for any bookable resource (room, table, desk, booth, court, bay, machine, slot).
 */
public record ResourceId(UUID value) implements DomainId<UUID> {
    public ResourceId {
        Objects.requireNonNull(value, "ResourceId value cannot be null");
    }

    public static ResourceId of(UUID value) {
        return new ResourceId(value);
    }

    public static ResourceId of(String value) {
        return new ResourceId(UUID.fromString(value));
    }

    public static ResourceId generate() {
        return new ResourceId(UUID.randomUUID());
    }
}
