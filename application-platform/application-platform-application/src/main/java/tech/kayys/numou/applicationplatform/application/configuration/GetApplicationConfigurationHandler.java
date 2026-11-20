package tech.kayys.numou.applicationplatform.application.configuration;

import tech.kayys.numou.applicationplatform.application.installation.ApplicationInstallationActorProvider;
import tech.kayys.numou.applicationplatform.application.installation.InstallationActor;
import tech.kayys.numou.applicationplatform.domain.configuration.ApplicationConfigurationSnapshot;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Authorizes and retrieves the current tenant-scoped application configuration. */
public final class GetApplicationConfigurationHandler {
    private final ApplicationInstallationActorProvider actors;
    private final ApplicationConfigurationReadAuthorizationPort authorization;
    private final ApplicationConfigurationStore store;

    public GetApplicationConfigurationHandler(
            ApplicationInstallationActorProvider actors,
            ApplicationConfigurationReadAuthorizationPort authorization,
            ApplicationConfigurationStore store) {
        this.actors = Objects.requireNonNull(actors, "actors");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.store = Objects.requireNonNull(store, "store");
    }

    public CompletionStage<Optional<ApplicationConfigurationSnapshot>> handle(
            GetApplicationConfigurationQuery query) {
        Objects.requireNonNull(query, "query");
        final InstallationActor actor = Objects.requireNonNull(
                actors.currentActor(), "verified actor context");
        return authorization.requireCanRead(actor, query.applicationId())
                .thenCompose(ignored -> store.findCurrent(actor.tenantId(), query.applicationId().value()));
    }
}
