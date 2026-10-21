package tech.kayys.numou.mobility.integration;

import tech.kayys.numou.aggregate.memory.DispatchingEventSink;
import tech.kayys.numou.mobility.kernel.domain.RideId;
import tech.kayys.numou.mobility.ride.application.RideSettlementService;
import tech.kayys.numou.mobility.ride.domain.event.RideCancelled;
import tech.kayys.numou.mobility.ride.domain.event.RideCompleted;
import tech.kayys.numou.mobility.ride.domain.event.RideNoDrivers;

/**
 * Event-driven reactions. The ride services already settle inline right after a ride ends; these policies are the second,
 * independent trigger (a redelivered event, or inline settlement that failed). Settlement is idempotent, so the overlap is by design.
 */
public final class MobilityPolicies {

    private MobilityPolicies() { }

    public static void register(DispatchingEventSink bus, RideSettlementService settlement) {
        bus.on(RideCompleted.class, (tenant, e) -> settlement.settle(tenant, RideId.of(e.rideId())));
        bus.on(RideCancelled.class, (tenant, e) -> settlement.settle(tenant, RideId.of(e.rideId())));
        bus.on(RideNoDrivers.class, (tenant, e) -> settlement.settle(tenant, RideId.of(e.rideId())));
    }
}
