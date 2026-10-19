package tech.kayys.numou.organization.application.command;

import tech.kayys.numou.organization.domain.OrganizationId;
import tech.kayys.numou.organization.domain.OrganizationUnitId;

public record CreateOrganizationUnitCommand(
        OrganizationId organizationId,
        OrganizationUnitId parentUnitId,
        String name,
        String code
) {}
