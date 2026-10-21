package tech.kayys.numou.education.pesantren.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

public record LeavePermitId(UUID value) implements DomainId<UUID> {
    public static LeavePermitId of(UUID v) { return new LeavePermitId(v); }
    public static LeavePermitId generate() { return new LeavePermitId(UUID.randomUUID()); }
}
