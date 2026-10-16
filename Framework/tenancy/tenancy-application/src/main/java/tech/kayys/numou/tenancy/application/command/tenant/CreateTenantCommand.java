package tech.kayys.numou.tenancy.application.command.tenant;

import tech.kayys.numou.foundation.domain.ref.OrganizationRef;

/** Command to register a new Tenant (starts in PROVISIONING state). */
public record CreateTenantCommand(
        String code,
        String name,
        OrganizationRef organization,   // nullable — organization is optional at creation
        String actor
) {}
