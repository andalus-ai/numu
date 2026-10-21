package tech.kayys.numou.mobility.ride.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.aggregate.spi.DomainEventSink;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.mobility.ride.domain.Ride;
import tech.kayys.numou.mobility.ride.domain.RideStatus;
import tech.kayys.numou.mobility.ride.domain.event.RideFlagged;
import tech.kayys.numou.mobility.ride.spi.RideRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cheap, explainable fraud signals evaluated when a ride completes. A flagged ride is reported to operations (event + alert); nothing is
 * blocked automatically, because every signal also has an innocent explanation (a regular commuter really does ride with the same driver).
 *
 * <ul>
 *   <li><b>PAIR_REPEAT</b>: the same rider and driver completed rides together several times this week (collusion to farm promos/incentives).</li>
 *   <li><b>PROMO_SHORT_TRIP</b>: a discounted trip of under 1 km (promo farming).</li>
 *   <li><b>PROMO_VELOCITY</b>: three or more promo rides by this rider in 24 hours.</li>
 *   <li><b>NO_MOVEMENT</b>: the recorded route is under 30% of the straight-line distance (GPS spoofing or a trip that never happened).</li>
 * </ul>
 * Scores add up (cap 100); 60 or more is flagged.
 */
public final class RiskService {

    public static final int FLAG_THRESHOLD = 60;

    public record Assessment(int score, List<String> reasons) {
        public boolean flagged() { return score >= FLAG_THRESHOLD; }
    }

    private final RideRepository rides;
    private final DomainEventSink events;
    private final DomainClock clock;

    public RiskService(RideRepository rides, DomainEventSink events, DomainClock clock) {
        this.rides = rides;
        this.events = events;
        this.clock = clock;
    }

    public Uni<Assessment> assessCompleted(UUID tenantId, Ride ride) {
        if (ride.status() != RideStatus.COMPLETED || ride.driverId().isEmpty()) {
            return Uni.createFrom().item(new Assessment(0, List.of()));
        }
        final UUID driver = ride.driverId().get();
        final var now = clock.now();
        return rides.history(tenantId, ride.riderId(), 50).map(history -> {
            int score = 0;
            final List<String> reasons = new ArrayList<>();
            final long priorWithDriver = history.stream()
                    .filter(r -> !r.id().equals(ride.id()) && r.status() == RideStatus.COMPLETED && r.driverId().filter(driver::equals).isPresent()
                            && r.completedAt() != null && Duration.between(r.completedAt(), now).compareTo(Duration.ofDays(7)) <= 0).count();
            if (priorWithDriver >= 3) { score += 60; reasons.add("PAIR_REPEAT"); } else if (priorWithDriver == 2) { score += 30; reasons.add("PAIR_REPEAT_MILD"); }
            final boolean promo = ride.quote().promo() != null && ride.quote().discount().signum() > 0;
            final double straight = ride.pickup().point().distanceTo(ride.dropoff().point());
            if (promo && straight < 1_000) { score += 30; reasons.add("PROMO_SHORT_TRIP"); }
            if (promo) {
                final long promoRides = history.stream().filter(r -> r.quote().promo() != null && r.quote().discount().signum() > 0
                        && Duration.between(r.requestedAt(), now).compareTo(Duration.ofHours(24)) <= 0).count();
                if (promoRides >= 3) { score += 40; reasons.add("PROMO_VELOCITY"); }
            }
            if (ride.trackedMeters() < 0.3 * straight) { score += 50; reasons.add("NO_MOVEMENT"); }
            return new Assessment(Math.min(score, 100), List.copyOf(reasons));
        });
    }

    /** Assess and, if flagged, tell operations. Never fails the caller. */
    public Uni<Void> assessAndReport(UUID tenantId, Ride ride) {
        return assessCompleted(tenantId, ride).flatMap(a -> !a.flagged() ? Uni.createFrom().voidItem()
                : events.publish(tenantId, List.of(RideFlagged.at(clock.now(), ride.id().value(), ride.riderId().value(), ride.driverId().orElse(null),
                        a.score(), String.join(",", a.reasons()))))).onFailure().recoverWithItem((Void) null);
    }
}
