package tech.kayys.numou.foundation.application.runtime.lifecycle;

import io.smallrye.mutiny.Uni;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe default implementation of DrainController.
 */
public final class DefaultDrainController implements DrainController {

    private final AtomicLong inFlight = new AtomicLong(0);
    private final AtomicBoolean draining = new AtomicBoolean(false);
    private final CompletableFuture<Void> drained = new CompletableFuture<>();

    @Override
    public void beginRequest() {
        if (draining.get()) {
            throw new RejectedExecutionException("Runtime is draining; cannot accept new requests");
        }

        inFlight.incrementAndGet();

        // Race protection: if drain began between check and increment
        if (draining.get()) {
            completeRequest();
            throw new RejectedExecutionException("Runtime began draining; rejected request");
        }
    }

    @Override
    public void completeRequest() {
        long remaining = inFlight.decrementAndGet();
        if (remaining <= 0 && draining.get()) {
            drained.complete(null);
        }
    }

    @Override
    public void beginDrain() {
        if (draining.compareAndSet(false, true) && inFlight.get() <= 0) {
            drained.complete(null);
        }
    }

    @Override
    public Uni<Void> awaitDrained() {
        return Uni.createFrom().completionStage(drained);
    }

    @Override
    public long inFlight() {
        return Math.max(0, inFlight.get());
    }
}
