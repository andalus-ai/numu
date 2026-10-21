package tech.kayys.numou.mobility.ride.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.mobility.kernel.domain.VehicleType;
import tech.kayys.numou.mobility.kernel.geo.GeoMath;
import tech.kayys.numou.mobility.kernel.geo.LatLng;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.kayys.numou.mobility.ride.domain.RideDomainTest.*;

class RideSafetyAndRefundTest {

    static LatLng onRoute(double metersNorth, double metersEast) { return GeoMath.offset(MONAS, metersNorth, metersEast); }

    @Test
    void threeConsecutiveFixesFarOffTheCorridorRaiseOneDeviationEvent() {
        final var r = inProgress(D1); r.pullDomainEvents();
        final var far = onRoute(1_800, 4_000);                                            // 4 km east of a north-south trip
        assertThat(r.observeDeviation(far, T0.plusSeconds(300))).isTrue();
        assertThat(r.observeDeviation(far, T0.plusSeconds(310))).isTrue();
        assertThat(r.pullDomainEvents()).isEmpty();                                        // two fixes: not yet
        r.observeDeviation(far, T0.plusSeconds(320));
        assertThat(r.pullDomainEvents()).extracting(e -> e.eventType()).containsExactly("mobility.ride.route-deviated");
        r.observeDeviation(far, T0.plusSeconds(330));
        assertThat(r.pullDomainEvents()).isEmpty();                                        // raised once per ride, not on every fix
        assertThat(r.deviationFlaggedAt()).isNotNull();
    }

    @Test
    void returningToTheRouteResetsTheStreakAndNormalDrivingNeverWrites() {
        final var r = inProgress(D1);
        assertThat(r.observeDeviation(onRoute(800, 100), T0.plusSeconds(300))).isFalse();      // on route: nothing to save
        r.observeDeviation(onRoute(800, 4_000), T0.plusSeconds(310));
        r.observeDeviation(onRoute(800, 4_000), T0.plusSeconds(320));
        assertThat(r.offRouteStreak()).isEqualTo(2);
        assertThat(r.observeDeviation(onRoute(1_000, 50), T0.plusSeconds(330))).isTrue();      // back: the reset is saved
        assertThat(r.offRouteStreak()).isZero();
        assertThat(r.observeDeviation(onRoute(1_200, 50), T0.plusSeconds(340))).isFalse();
    }

    @Test
    void deviationIsOnlyWatchedDuringTheTrip() {
        assertThat(assigned(D1).observeDeviation(onRoute(0, 9_000), T0.plusSeconds(100))).isFalse();
    }

    @Test
    void theAllowedCorridorGrowsWithTheLengthOfTheTrip() {
        final var r = inProgress(D1);            // 3.7 km trip: corridor = max(1.5 km, 1.85 km) = 1.85 km
        assertThat(r.observeDeviation(onRoute(1_800, 1_700), T0.plusSeconds(300))).isFalse();   // a 1.7 km sidestep is a normal road detour
        assertThat(r.observeDeviation(onRoute(1_800, 2_100), T0.plusSeconds(310))).isTrue();
    }

    @Test
    void appliedDistanceFromTheTraceNeverShrinksLiveTracking() {
        final var r = inProgress(D1);
        r.applyTrackedDistance(3_000);
        r.applyTrackedDistance(2_000);
        assertThat(r.trackedMeters()).isEqualTo(3_000);
        assertThatThrownBy(() -> assigned(D1).applyTrackedDistance(1)).isInstanceOf(InvalidStateException.class);
    }

    @Test
    void traceDistanceMatchesWhatLiveTrackingWouldHaveCounted() {
        final var live = inProgress(D1);
        final List<TracePoint> trace = new ArrayList<>(List.of(new TracePoint(MONAS, live.startedAt())));
        LatLng p = MONAS; Instant t = live.startedAt();
        for (int i = 0; i < 12; i++) {
            t = t.plusSeconds(40); p = GeoMath.offset(p, 300, 0);
            live.recordPosition(p, t); trace.add(new TracePoint(p, t));
        }
        t = t.plusSeconds(5); final var teleport = GeoMath.offset(p, 40_000, 0);
        live.recordPosition(teleport, t); trace.add(new TracePoint(teleport, t));           // glitch: both ignore it
        t = t.plusSeconds(40); p = GeoMath.offset(p, 300, 0);
        live.recordPosition(p, t); trace.add(new TracePoint(p, t));
        assertThat(TripTraceMath.trackedDistanceMeters(trace, VehicleType.CAR)).isEqualTo(live.trackedMeters(), org.assertj.core.data.Offset.offset(0.001));
        assertThat(TripTraceMath.trackedDistanceMeters(List.of(trace.get(0)), VehicleType.CAR)).isZero();
    }

    // ---------------------------------------------------------------- refunds

    static Ride completed() {
        final var r = inProgress(D1);
        r.complete(D1, S, drive(r, 10, 520, 95));
        return r;
    }

    @Test
    void refundsAreCappedByWhatWasPaidAndIdempotentPerReference() {
        final var r = completed(); final BigDecimal paid = r.finalFare().total(); r.pullDomainEvents();
        assertThat(r.recordRefund("case-1", new BigDecimal("5000"), r.completedAt())).isTrue();
        assertThat(r.recordRefund("case-1", new BigDecimal("5000"), r.completedAt())).isFalse();   // support double-clicked
        assertThat(r.refunded()).isEqualByComparingTo("5000");
        assertThatThrownBy(() -> r.recordRefund("case-2", paid, r.completedAt())).hasMessageContaining("exceeds");
        assertThat(r.recordRefund("case-3", paid.subtract(new BigDecimal("5000")), r.completedAt())).isTrue();   // exactly the rest
        assertThat(r.refunded()).isEqualByComparingTo(paid);
        assertThat(r.pullDomainEvents()).extracting(e -> e.eventType()).containsExactly("mobility.ride.refunded", "mobility.ride.refunded");
    }

    @Test
    void onlyPaidRidesCanBeRefundedAndAmountsMustBePositive() {
        assertThatThrownBy(() -> ride().recordRefund("x", BigDecimal.TEN, T0)).isInstanceOf(InvalidStateException.class);
        assertThatThrownBy(() -> completed().recordRefund("x", BigDecimal.ZERO, T0)).isInstanceOf(BusinessRuleViolation.class);
        final var feeRide = assigned(D1); feeRide.cancelByRider("late", POLICY, S, T0.plusSeconds(300));
        assertThat(feeRide.recordRefund("fee-waiver", new BigDecimal("8000"), T0.plusSeconds(400))).isTrue();     // a cancellation fee can be refunded too
        final var freeRide = ride(); freeRide.cancelByRider("x", POLICY, S, T0.plusSeconds(5));
        assertThatThrownBy(() -> freeRide.recordRefund("y", BigDecimal.TEN, T0)).isInstanceOf(InvalidStateException.class);
    }

    @Test
    void snapshotKeepsDeviationAndRefundState() {
        final var r = completed(); r.recordRefund("case-1", new BigDecimal("1000"), r.completedAt());
        final var back = Ride.restore(r.snapshot());
        assertThat(back.snapshot()).isEqualTo(r.snapshot());
        assertThat(back.recordRefund("case-1", new BigDecimal("1000"), r.completedAt())).isFalse();             // idempotency survives a reload
    }
}
