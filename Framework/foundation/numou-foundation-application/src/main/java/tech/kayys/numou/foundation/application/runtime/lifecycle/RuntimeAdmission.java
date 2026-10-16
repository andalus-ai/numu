package tech.kayys.numou.foundation.application.runtime.lifecycle;

import io.smallrye.mutiny.Uni;

/**
 * Gate controlling whether the application runtime accepts new incoming work (config02.md §P4-08 #4).
 */
public interface RuntimeAdmission {

    AdmissionState state();

    boolean acceptsNewWork();

    void open();

    void close();

    void beginDraining();

    Uni<Void> drain();
}
