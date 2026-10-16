package tech.kayys.syirkah.tenancy.application.command.settings;

import tech.kayys.syirkah.tenancy.domain.settings.TimeZoneId;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;

public record ChangeTenantTimezoneCommand(TenantId tenantId, TimeZoneId timezone, String actor) {}
