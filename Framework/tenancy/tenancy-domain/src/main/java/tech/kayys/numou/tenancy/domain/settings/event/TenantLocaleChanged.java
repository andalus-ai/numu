package tech.kayys.syirkah.tenancy.domain.settings.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.tenancy.domain.settings.LocaleCode;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;

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
