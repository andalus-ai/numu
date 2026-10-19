package tech.kayys.numou.foundation.application.runtime.lifecycle;

import io.smallrye.mutiny.Uni;

/**
 * Controller tracking in-flight work and coordinating draining during graceful shutdown (config02.md §P4-08 #7).
 */
public interface DrainController {

    void beginRequest();

    void completeRequest();

    void beginDrain();

    Uni<Void> awaitDrained();

    long inFlight();
}
