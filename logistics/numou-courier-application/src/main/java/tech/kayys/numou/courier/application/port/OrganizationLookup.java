package tech.kayys.numou.courier.application.port;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.organization.domain.Organization;
import tech.kayys.numou.organization.domain.OrganizationId;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Tenant-scoped organization lookup. Implementations must predicate the
 * repository query on both tenant ID and organization ID; a global lookup
 * followed by application-memory filtering is not an acceptable boundary.
 */
public interface OrganizationLookup {
    CompletionStage<Optional<Organization>> findById(TenantId tenantId, OrganizationId organizationId);
}
