package tech.kayys.numou.tenancy.application.command.settings;

import tech.kayys.numou.tenancy.domain.settings.TimeZoneId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record ChangeTenantTimezoneCommand(TenantId tenantId, TimeZoneId timezone, String actor) {}
