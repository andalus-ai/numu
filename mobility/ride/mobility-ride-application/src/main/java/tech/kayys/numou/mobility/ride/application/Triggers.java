package tech.kayys.numou.mobility.ride.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.mobility.kernel.domain.RideId;

import java.util.UUID;

/** Seams that let the services trigger each other without a dependency cycle. Wired in the composition root. */
public final class Triggers {

    private Triggers() { }

    @FunctionalInterface
    public interface Dispatch {
        Uni<Void> dispatch(UUID tenantId, RideId rideId);
    }

    @FunctionalInterface
    public interface Settle {
        Uni<Void> settle(UUID tenantId, RideId rideId);
    }
}
