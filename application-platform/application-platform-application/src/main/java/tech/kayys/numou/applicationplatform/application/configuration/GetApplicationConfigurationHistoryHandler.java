package tech.kayys.numou.applicationplatform.application.configuration;

import tech.kayys.numou.applicationplatform.application.installation.ApplicationInstallationActorProvider;
import tech.kayys.numou.applicationplatform.application.installation.InstallationActor;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Authorizes and retrieves a bounded tenant-scoped configuration history page. */
public final class GetApplicationConfigurationHistoryHandler {
    private final ApplicationInstallationActorProvider actors;
    private final ApplicationConfigurationReadAuthorizationPort authorization;
    private final ApplicationConfigurationStore store;

    public GetApplicationConfigurationHistoryHandler(
            ApplicationInstallationActorProvider actors,
            ApplicationConfigurationReadAuthorizationPort authorization,
            ApplicationConfigurationStore store) {
        this.actors = Objects.requireNonNull(actors, "actors");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.store = Objects.requireNonNull(store, "store");
    }

    public CompletionStage<ApplicationConfigurationStore.HistoryPage> handle(
            GetApplicationConfigurationHistoryQuery query) {
        Objects.requireNonNull(query, "query");
        final InstallationActor actor = Objects.requireNonNull(
                actors.currentActor(), "verified actor context");
        return authorization.requireCanRead(actor, query.applicationId())
                .thenCompose(ignored -> store.findHistory(actor.tenantId(), query.applicationId(),
                        query.beforeRevision(), query.pageSize()));
    }
}
