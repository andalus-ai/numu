package tech.kayys.numou.leave.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;
import java.util.UUID;

public record LeaveTypeId(UUID value) implements DomainId<UUID> {
    public static LeaveTypeId of(UUID v) { return new LeaveTypeId(v); }
    public static LeaveTypeId generate() { return new LeaveTypeId(UUID.randomUUID()); }
}
