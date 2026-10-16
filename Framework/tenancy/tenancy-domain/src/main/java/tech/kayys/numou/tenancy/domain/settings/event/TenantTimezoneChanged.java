package tech.kayys.syirkah.tenancy.domain.settings.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.tenancy.domain.settings.TimeZoneId;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;

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
