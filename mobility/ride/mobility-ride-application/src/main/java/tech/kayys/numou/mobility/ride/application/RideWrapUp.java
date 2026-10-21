package tech.kayys.numou.mobility.ride.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.aggregate.application.ConflictRetry;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.mobility.driver.application.DriverService;
import tech.kayys.numou.mobility.ride.domain.Ride;
import tech.kayys.numou.mobility.ride.domain.RideStatus;
import tech.kayys.numou.mobility.ride.spi.ActiveRideLockRepository;
import tech.kayys.numou.mobility.ride.spi.PromoCatalog;
import tech.kayys.numou.mobility.ride.spi.RideNotifications;

import java.util.UUID;

/**
 * Everything that must happen once a ride ends (completed, cancelled or no drivers): free the rider's single-ride lock,
 * return the driver to the pool, give an unused promo back, tell both parties and trigger settlement.
 *
 * <p>Each step is idempotent and best-effort in isolation: a failing push notification must not stop the driver being
 * released, and a failure anywhere is repaired by redelivered events and the settlement sweeper.
 */
public final class RideWrapUp {

    private final ActiveRideLockRepository locks;
    private final DriverService drivers;
    private final PromoCatalog promos;
    private final RideNotifications notify;
    private final RiskService risk;
    private final Triggers.Settle settle;
    private final UnitOfWork uow;
    private final DomainClock clock;

    public RideWrapUp(ActiveRideLockRepository locks, DriverService drivers, PromoCatalog promos, RideNotifications notify,
                      RiskService risk, Triggers.Settle settle, UnitOfWork uow, DomainClock clock) {
        this.locks = locks;
        this.drivers = drivers;
        this.promos = promos;
        this.notify = notify;
        this.risk = risk;
        this.settle = settle;
        this.uow = uow;
        this.clock = clock;
    }

    public Uni<Void> afterEnd(UUID tenantId, Ride ride) {
        final UUID rider = ride.riderId().value();
        final boolean completed = ride.status() == RideStatus.COMPLETED;
        return quietly(releaseLock(tenantId, ride))
                .flatMap(x -> ride.driverId().map(d -> quietly(drivers.release(tenantId, d, ride.id()).replaceWithVoid())).orElse(Uni.createFrom().voidItem()))
                .flatMap(x -> !completed && ride.quote().promo() != null
                        ? quietly(promos.refund(tenantId, rider, ride.quote().promo().code(), ride.id().value()))
                        : Uni.createFrom().voidItem())
                .flatMap(x -> quietly(notify.rideUpdateToRider(tenantId, rider, ride.id().value(), ride.status().name())))
                .flatMap(x -> ride.driverId().map(d -> quietly(notify.rideUpdateToDriver(tenantId, d, ride.id().value(), ride.status().name()))).orElse(Uni.createFrom().voidItem()))
                .flatMap(x -> completed ? quietly(risk.assessAndReport(tenantId, ride)) : Uni.createFrom().voidItem())
                .flatMap(x -> quietly(settle.settle(tenantId, ride.id())));
    }

    private Uni<Void> releaseLock(UUID tenantId, Ride ride) {
        return ConflictRetry.run(5, () -> uow.execute(() -> locks.find(tenantId, ride.riderId()).flatMap(o -> {
            if (o.isEmpty()) return Uni.createFrom().voidItem();
            final var lock = o.get();
            return lock.release(ride.id(), clock.now()) ? locks.save(tenantId, lock) : Uni.createFrom().voidItem();
        })));
    }

    private static Uni<Void> quietly(Uni<Void> step) {
        return step.onFailure().recoverWithItem((Void) null);
    }
}
