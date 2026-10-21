package tech.kayys.numou.education.pesantren.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

public record DormitoryId(UUID value) implements DomainId<UUID> {
    public static DormitoryId of(UUID v) { return new DormitoryId(v); }
    public static DormitoryId generate() { return new DormitoryId(UUID.randomUUID()); }
}
