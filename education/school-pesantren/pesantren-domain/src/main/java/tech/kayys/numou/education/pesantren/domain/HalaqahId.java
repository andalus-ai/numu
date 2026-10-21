package tech.kayys.numou.education.pesantren.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

public record HalaqahId(UUID value) implements DomainId<UUID> {
    public static HalaqahId of(UUID v) { return new HalaqahId(v); }
    public static HalaqahId generate() { return new HalaqahId(UUID.randomUUID()); }
}
