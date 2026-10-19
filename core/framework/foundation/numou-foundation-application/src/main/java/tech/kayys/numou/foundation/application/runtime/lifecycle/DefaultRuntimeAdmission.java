package tech.kayys.numou.foundation.application.runtime.lifecycle;

import io.smallrye.mutiny.Uni;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default implementation of RuntimeAdmission backed by DrainController.
 */
public final class DefaultRuntimeAdmission implements RuntimeAdmission {

    private final AtomicReference<AdmissionState> state = new AtomicReference<>(AdmissionState.CLOSED);
    private final DrainController drainController;

    public DefaultRuntimeAdmission(DrainController drainController) {
        this.drainController = Objects.requireNonNull(drainController, "drainController cannot be null");
    }

    @Override
    public AdmissionState state() {
        return state.get();
    }

    @Override
    public boolean acceptsNewWork() {
        return state.get() == AdmissionState.OPEN;
    }

    @Override
    public void open() {
        state.compareAndSet(AdmissionState.CLOSED, AdmissionState.OPEN);
    }

    @Override
    public void close() {
        state.set(AdmissionState.CLOSED);
    }

    @Override
    public void beginDraining() {
        if (state.compareAndSet(AdmissionState.OPEN, AdmissionState.DRAINING)) {
            drainController.beginDrain();
        }
    }

    @Override
    public Uni<Void> drain() {
        beginDraining();
        return drainController.awaitDrained();
    }
}
