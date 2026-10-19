package tech.kayys.numou.scheduling.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.scheduling.domain.ScheduleId;

import java.time.Instant;
import java.util.UUID;

public record ScheduleCancelled(
        UUID eventId,
        Instant occurredAt,
        ScheduleId scheduleId,
        TenantId tenantId
) implements DomainEvent {
    public ScheduleCancelled(ScheduleId scheduleId, TenantId tenantId) {
        this(UUID.randomUUID(), Instant.now(), scheduleId, tenantId);
    }
    @Override
    public String eventType() {
        return "scheduling.schedule.cancelled";
    }
}
