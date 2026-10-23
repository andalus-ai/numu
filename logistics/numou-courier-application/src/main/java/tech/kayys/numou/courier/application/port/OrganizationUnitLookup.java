package tech.kayys.numou.courier.application.port;

import tech.kayys.numou.organization.domain.OrganizationId;
import tech.kayys.numou.organization.domain.OrganizationUnit;
import tech.kayys.numou.organization.domain.OrganizationUnitId;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Authorized lookup of an organizational unit. Implementations must scope the
 * lookup by organization and unit ID and must not expose cross-tenant records.
 */
public interface OrganizationUnitLookup {
    CompletionStage<Optional<OrganizationUnit>> findById(
            OrganizationId organizationId, OrganizationUnitId organizationUnitId);
}
