package tech.kayys.syirkah.ecosystem.domain.contact;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly typed identifier for a {@link ContactPoint} (config03.md §P4-13).
 */
public record ContactPointId(UUID value) implements DomainId<UUID> {

    public ContactPointId {
        Objects.requireNonNull(value, "value cannot be null");
    }

    public static ContactPointId generate() {
        return new ContactPointId(UUID.randomUUID());
    }

    public static ContactPointId of(UUID value) {
        return new ContactPointId(value);
    }

    public static ContactPointId fromString(String uuid) {
        return new ContactPointId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
