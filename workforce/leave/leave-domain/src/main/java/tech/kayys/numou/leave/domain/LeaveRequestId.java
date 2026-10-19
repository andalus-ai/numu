package tech.kayys.numou.leave.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;
import java.util.UUID;

public record LeaveRequestId(UUID value) implements DomainId<UUID> {
    public static LeaveRequestId of(UUID v) { return new LeaveRequestId(v); }
    public static LeaveRequestId generate() { return new LeaveRequestId(UUID.randomUUID()); }
}
