package tech.kayys.syirkah.organization.application.command;

import tech.kayys.syirkah.organization.domain.OrganizationId;
import tech.kayys.syirkah.organization.domain.OrganizationUnitId;

public record CreateOrganizationUnitCommand(
        OrganizationId organizationId,
        OrganizationUnitId parentUnitId,
        String name,
        String code
) {}
