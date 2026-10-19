package tech.kayys.numou.foundation.application.runtime;

import io.smallrye.mutiny.Uni;

/**
 * Canonical Application Runtime contract (config01.md §P4-01 #3, §P4-05 #5).
 */
public interface ApplicationRuntime {

    ApplicationRuntimeState state();

    Uni<Void> start();

    Uni<Void> stop();

    boolean isRunning();

    RuntimeRegistry registry();
}
