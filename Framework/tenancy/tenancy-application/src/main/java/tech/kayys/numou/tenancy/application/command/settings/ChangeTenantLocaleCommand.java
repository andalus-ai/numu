package tech.kayys.syirkah.tenancy.application.command.settings;

import tech.kayys.syirkah.tenancy.domain.settings.LocaleCode;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;

public record ChangeTenantLocaleCommand(TenantId tenantId, LocaleCode locale, String actor) {}
