package tech.kayys.numou.courier.application.port;

import tech.kayys.numou.organization.domain.Organization;
import tech.kayys.numou.organization.domain.OrganizationId;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Looks up an organization by ID. Implementations must apply the authenticated
 * tenant boundary in the query, not load an arbitrary organization and filter
 * it in application memory.
 */
public interface OrganizationLookup {
    CompletionStage<Optional<Organization>> findById(OrganizationId organizationId);
}
