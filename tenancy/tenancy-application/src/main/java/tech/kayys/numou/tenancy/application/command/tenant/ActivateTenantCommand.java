package tech.kayys.numou.tenancy.application.command.tenant;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record ActivateTenantCommand(TenantId tenantId, String actor) {}
