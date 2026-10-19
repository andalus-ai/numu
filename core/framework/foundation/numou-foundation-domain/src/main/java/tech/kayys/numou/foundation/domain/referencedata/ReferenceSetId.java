package tech.kayys.numou.foundation.domain.referencedata;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly typed identifier for a reference data set (config03.md §P4-16 #3).
 */
public record ReferenceSetId(UUID value) implements DomainId<UUID> {

    public ReferenceSetId {
        Objects.requireNonNull(value, "value cannot be null");
    }

    public static ReferenceSetId generate() {
        return new ReferenceSetId(UUID.randomUUID());
    }

    public static ReferenceSetId of(UUID value) {
        return new ReferenceSetId(value);
    }

    public static ReferenceSetId fromString(String uuid) {
        return new ReferenceSetId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
