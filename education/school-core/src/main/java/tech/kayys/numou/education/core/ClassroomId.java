package tech.kayys.numou.education.core;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

/** Shared-kernel identifier so other education contexts can reference a classroom without depending on its owner. */
public record ClassroomId(UUID value) implements DomainId<UUID> {
    public static ClassroomId of(UUID v) { return new ClassroomId(v); }
    public static ClassroomId generate() { return new ClassroomId(UUID.randomUUID()); }
}
