package tech.kayys.numou.foundation.domain.referencedata;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly typed identifier for a reference data entry (config03.md §P4-16 #3).
 */
public record ReferenceEntryId(UUID value) implements DomainId<UUID> {

    public ReferenceEntryId {
        Objects.requireNonNull(value, "value cannot be null");
    }

    public static ReferenceEntryId generate() {
        return new ReferenceEntryId(UUID.randomUUID());
    }

    public static ReferenceEntryId of(UUID value) {
        return new ReferenceEntryId(value);
    }

    public static ReferenceEntryId fromString(String uuid) {
        return new ReferenceEntryId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
