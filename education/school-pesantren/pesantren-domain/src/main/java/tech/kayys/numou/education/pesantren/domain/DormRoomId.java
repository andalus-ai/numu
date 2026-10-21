package tech.kayys.numou.education.pesantren.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

public record DormRoomId(UUID value) implements DomainId<UUID> {
    public static DormRoomId of(UUID v) { return new DormRoomId(v); }
    public static DormRoomId generate() { return new DormRoomId(UUID.randomUUID()); }
}
