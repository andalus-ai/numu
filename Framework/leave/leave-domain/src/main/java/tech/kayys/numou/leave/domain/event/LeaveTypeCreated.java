package tech.kayys.numou.leave.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.leave.domain.LeaveTypeId;

import java.time.Instant;
import java.util.UUID;

public record LeaveTypeCreated(
        UUID eventId,
        Instant occurredAt,
        LeaveTypeId leaveTypeId,
        TenantId tenantId,
        String code,
        String name
) implements DomainEvent {
    public LeaveTypeCreated(LeaveTypeId leaveTypeId, TenantId tenantId, String code, String name) {
        this(UUID.randomUUID(), Instant.now(), leaveTypeId, tenantId, code, name);
    }
    @Override
    public String eventType() {
        return "leave.type.created";
    }
}
