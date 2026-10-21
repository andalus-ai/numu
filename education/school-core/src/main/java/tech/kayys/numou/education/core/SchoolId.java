package tech.kayys.numou.education.core;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

/** Shared-kernel identifier so other education contexts can reference a school without depending on its owner. */
public record SchoolId(UUID value) implements DomainId<UUID> {
    public static SchoolId of(UUID v) { return new SchoolId(v); }
    public static SchoolId generate() { return new SchoolId(UUID.randomUUID()); }
}
