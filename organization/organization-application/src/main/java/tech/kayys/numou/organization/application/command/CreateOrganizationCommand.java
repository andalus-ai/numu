package tech.kayys.numou.organization.application.command;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record CreateOrganizationCommand(
        TenantId tenantId,
        String name,
        String legalName,
        String registrationNumber
) {}
