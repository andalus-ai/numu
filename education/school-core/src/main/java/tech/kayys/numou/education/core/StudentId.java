package tech.kayys.numou.education.core;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

/** Shared-kernel identifier so other education contexts can reference a student without depending on its owner. */
public record StudentId(UUID value) implements DomainId<UUID> {
    public static StudentId of(UUID v) { return new StudentId(v); }
    public static StudentId generate() { return new StudentId(UUID.randomUUID()); }
}
