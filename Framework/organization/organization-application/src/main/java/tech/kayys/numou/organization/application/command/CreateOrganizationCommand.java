package tech.kayys.syirkah.organization.application.command;

import tech.kayys.syirkah.foundation.domain.tenant.TenantId;

public record CreateOrganizationCommand(
        TenantId tenantId,
        String name,
        String legalName,
        String registrationNumber
) {}
