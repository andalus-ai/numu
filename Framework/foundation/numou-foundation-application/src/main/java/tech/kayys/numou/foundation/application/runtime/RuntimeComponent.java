package tech.kayys.numou.foundation.application.runtime;

import io.smallrye.mutiny.Uni;

/**
 * Pluggable lifecycle-aware runtime component (config01.md §P4-05 #12).
 */
public interface RuntimeComponent {

    String id();

    Uni<Void> start();

    Uni<Void> stop();
}
