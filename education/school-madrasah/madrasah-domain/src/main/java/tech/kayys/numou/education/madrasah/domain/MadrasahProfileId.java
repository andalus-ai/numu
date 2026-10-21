package tech.kayys.numou.education.madrasah.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

public record MadrasahProfileId(UUID value) implements DomainId<UUID> {
    public static MadrasahProfileId of(UUID v) { return new MadrasahProfileId(v); }
    public static MadrasahProfileId generate() { return new MadrasahProfileId(UUID.randomUUID()); }
}
