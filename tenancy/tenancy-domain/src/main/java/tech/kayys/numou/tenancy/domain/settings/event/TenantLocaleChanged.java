package tech.kayys.numou.tenancy.domain.settings.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.tenancy.domain.settings.LocaleCode;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.UUID;

public record TenantLocaleChanged(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        LocaleCode locale
) implements DomainEvent {
    @Override public String eventType() { return "tenancy.settings.locale_changed"; }
}
