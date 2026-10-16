package tech.kayys.syirkah.tenancy.application.command.tenant;

import tech.kayys.syirkah.foundation.domain.tenant.TenantId;

public record ActivateTenantCommand(TenantId tenantId, String actor) {}
