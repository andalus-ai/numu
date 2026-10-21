package tech.kayys.numou.education.pesantren.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

public record DisciplineRecordId(UUID value) implements DomainId<UUID> {
    public static DisciplineRecordId of(UUID v) { return new DisciplineRecordId(v); }
    public static DisciplineRecordId generate() { return new DisciplineRecordId(UUID.randomUUID()); }
}
