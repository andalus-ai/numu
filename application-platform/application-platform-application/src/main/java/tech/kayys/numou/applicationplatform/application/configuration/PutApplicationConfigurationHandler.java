package tech.kayys.numou.applicationplatform.application.configuration;

import tech.kayys.numou.applicationplatform.application.installation.InstallationActor;
import tech.kayys.numou.applicationplatform.application.installation.ApplicationInstallationActorProvider;
import tech.kayys.numou.applicationplatform.domain.configuration.ApplicationConfigurationSnapshot;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Validates, authorizes and revision-CAS persists tenant application configuration. */
public final class PutApplicationConfigurationHandler {
    private final ApplicationInstallationActorProvider actors;
    private final ApplicationConfigurationAuthorizationPort authorization;
    private final ApplicationConfigurationSchemaPort schemas;
    private final ApplicationConfigurationStore store;
    private final Clock clock;

    public PutApplicationConfigurationHandler(ApplicationInstallationActorProvider actors,
            ApplicationConfigurationAuthorizationPort authorization, ApplicationConfigurationSchemaPort schemas,
            ApplicationConfigurationStore store, Clock clock) {
        this.actors = Objects.requireNonNull(actors);
        this.authorization = Objects.requireNonNull(authorization);
        this.schemas = Objects.requireNonNull(schemas);
        this.store = Objects.requireNonNull(store);
        this.clock = Objects.requireNonNull(clock);
    }

    public CompletionStage<ApplicationConfigurationStore.SaveResult> handle(PutApplicationConfigurationCommand command) {
        Objects.requireNonNull(command, "command");
        final InstallationActor actor = Objects.requireNonNull(
                actors.currentActor(), "verified actor context");
        return authorization.requireCanConfigure(actor, command.applicationId())
                .thenCompose(ignored -> schemas.validate(command.applicationId(),
                        command.schemaVersion(), command.payloadJson()))
                .thenCompose(validated -> store.findCurrent(actor.tenantId(), command.applicationId().value())
                        .thenCompose(current -> {
                            long actualRevision = current.map(ApplicationConfigurationSnapshot::revision).orElse(0L);
                            if (actualRevision != command.expectedRevision())
                                return CompletableFuture.completedFuture(
                                        new ApplicationConfigurationStore.SaveResult(false, current));
                            var snapshot = current
                                    .map(existing -> existing.next(command.schemaVersion(),
                                            validated.normalizedPayloadJson(), actor.actorId(), clock.instant()))
                                    .orElseGet(() -> ApplicationConfigurationSnapshot.first(actor.tenantId(),
                                            command.applicationId(), command.schemaVersion(),
                                            validated.normalizedPayloadJson(), actor.actorId(), clock.instant()));
                            return store.save(snapshot, command.expectedRevision());
                        }));
    }
}
