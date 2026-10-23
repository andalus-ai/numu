package tech.kayys.numou.courier.application.command;

import tech.kayys.numou.courier.domain.HubAddress;
import tech.kayys.numou.courier.domain.HubType;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.organization.domain.OrganizationId;
import tech.kayys.numou.organization.domain.OrganizationUnitId;

import java.time.ZoneId;
import java.util.Objects;

/** Immutable input to register a hub; actor authorization is supplied separately. */
public record RegisterHubCommand(
        TenantId tenantId,
        OrganizationId organizationId,
        OrganizationUnitId branchUnitId,
        String code,
        String name,
        HubType type,
        HubAddress address,
        ZoneId timeZone) {

    public RegisterHubCommand {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(branchUnitId, "branchUnitId must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(address, "address must not be null");
        Objects.requireNonNull(timeZone, "timeZone must not be null");
    }
}
