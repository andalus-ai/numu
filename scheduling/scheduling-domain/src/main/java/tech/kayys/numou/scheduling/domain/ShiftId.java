package tech.kayys.numou.scheduling.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;
import java.util.UUID;

public record ShiftId(UUID value) implements DomainId<UUID> {
    public static ShiftId of(UUID v) { return new ShiftId(v); }
    public static ShiftId generate() { return new ShiftId(UUID.randomUUID()); }
}
