package tech.kayys.numou.tenancy.application.command.tenant;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record DeactivateTenantCommand(TenantId tenantId, String actor) {}
