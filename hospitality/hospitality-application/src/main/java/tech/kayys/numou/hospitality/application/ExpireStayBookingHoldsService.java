package tech.kayys.numou.hospitality.application;

import io.smallrye.mutiny.Uni;
import java.time.Clock;
import java.time.Objects;
import tech.kayys.numou.hospitality.spi.StayBookingLifecyclePort;

/**
 * One bounded expiry pass. The host scheduler owns cadence, leader election and shutdown;
 * the adapter owns transactional row claiming and durable state transitions.
 */
public final class ExpireStayBookingHoldsService {
    private final StayBookingLifecyclePort lifecycle;
    private final Clock clock;
    private final int batchSize;

    public ExpireStayBookingHoldsService(StayBookingLifecyclePort lifecycle, Clock clock, int batchSize) {
        this.lifecycle = java.util.Objects.requireNonNull(lifecycle, "lifecycle");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("batchSize must be between 1 and 1000");
        }
        this.batchSize = batchSize;
    }

    /** Expires one bounded batch and returns the number of bookings transitioned. */
    public Uni<Integer> runOnce() {
        return lifecycle.expireDue(clock.instant(), batchSize);
    }
}
