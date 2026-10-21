package tech.kayys.numou.education.core;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

/** Shared-kernel identifier so other education contexts can reference a teacher without depending on its owner. */
public record TeacherId(UUID value) implements DomainId<UUID> {
    public static TeacherId of(UUID v) { return new TeacherId(v); }
    public static TeacherId generate() { return new TeacherId(UUID.randomUUID()); }
}
