package tech.kayys.numou.tenancy.domain.settings.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.tenancy.domain.settings.TimeZoneId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.UUID;

public record TenantTimezoneChanged(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        TimeZoneId timezone
) implements DomainEvent {
    @Override public String eventType() { return "tenancy.settings.timezone_changed"; }
}
