package tech.kayys.numou.foundation.application.runtime.lifecycle;

import io.smallrye.mutiny.Uni;

/**
 * Coordinates and executes the multi-phase graceful shutdown protocol (config02.md §P4-08 #13).
 */
public interface ShutdownCoordinator {

    Uni<ShutdownResult> shutdown();

    boolean isShutdownStarted();

    boolean isShutdownComplete();
}
