package tech.kayys.numou.foundation.application.runtime;

import io.smallrye.mutiny.Uni;

/**
 * Background managed worker contract (config01.md §P4-05 #13).
 */
public interface ManagedWorker {

    String id();

    Uni<Void> start();

    Uni<Void> stop();

    boolean isRunning();
}
