package tech.kayys.numou.support.application.api;

import io.smallrye.mutiny.Uni;

public interface SupportActorProvider {

    Uni<ActorContext> currentActor();
}
