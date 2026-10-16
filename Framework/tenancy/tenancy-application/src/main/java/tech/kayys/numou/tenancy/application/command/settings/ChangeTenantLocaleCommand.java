package tech.kayys.numou.tenancy.application.command.settings;

import tech.kayys.numou.tenancy.domain.settings.LocaleCode;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record ChangeTenantLocaleCommand(TenantId tenantId, LocaleCode locale, String actor) {}
