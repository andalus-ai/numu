package tech.kayys.numou.education.pesantren.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

public record SantriId(UUID value) implements DomainId<UUID> {
    public static SantriId of(UUID v) { return new SantriId(v); }
    public static SantriId generate() { return new SantriId(UUID.randomUUID()); }
}
