package tech.kayys.numou.education.pesantren.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

public record HafalanLogId(UUID value) implements DomainId<UUID> {
    public static HafalanLogId of(UUID v) { return new HafalanLogId(v); }
    public static HafalanLogId generate() { return new HafalanLogId(UUID.randomUUID()); }
}
