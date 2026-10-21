package tech.kayys.numou.identity.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.identity.domain.idp.FederatedIdentityLink;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for the link between an external provider subject and an internal user.
 */
public interface FederatedIdentityRepository {

    Uni<Optional<FederatedIdentityLink>> findByProviderAndSubject(UUID providerId, String subject);

    /** Reverse lookup for SCIM projections: every external link one internal user has. */
    Uni<Optional<FederatedIdentityLink>> findByUserId(UUID userId);

    Uni<Void> save(FederatedIdentityLink link);

    /**
     * Saves a federated link together with the provider's custom attributes
     * ({@code claims}), persisted as JSON on the link row.
     */
    Uni<Void> save(FederatedIdentityLink link, java.util.Map<String, String> claims);
}
