package tech.kayys.numou.scheduling.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;
import java.util.UUID;

public record ScheduleId(UUID value) implements DomainId<UUID> {
    public static ScheduleId of(UUID v) { return new ScheduleId(v); }
    public static ScheduleId generate() { return new ScheduleId(UUID.randomUUID()); }
}
