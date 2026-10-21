package tech.kayys.numou.mobility.ride.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.mobility.kernel.domain.RideId;
import tech.kayys.numou.mobility.kernel.domain.RiderId;
import tech.kayys.numou.mobility.kernel.domain.VehicleType;
import tech.kayys.numou.mobility.kernel.geo.GeoMath;
import tech.kayys.numou.mobility.kernel.geo.LatLng;
import tech.kayys.numou.mobility.kernel.geo.Place;
import tech.kayys.numou.mobility.pricing.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RideDomainTest {

    static final Instant T0 = Instant.parse("2026-10-10T10:00:00Z");
    static final FareSchedule S = FareSchedule.indonesiaDefaults();
    static final CancellationPolicy POLICY = CancellationPolicy.defaults();
    static final LatLng MONAS = new LatLng(-6.1754, 106.8272);
    static final Duration WINDOW = Duration.ofSeconds(90);
    static final Duration TTL = Duration.ofSeconds(15);
    static final String PIN = "4821";
    static final UUID D1 = UUID.randomUUID(), D2 = UUID.randomUUID(), D3 = UUID.randomUUID();

    static FareQuote quote(VehicleType v, Instant at) {
        return FareCalculator.quote(S, v, 5_000, 900, new BigDecimal("1.0"), null, at, Duration.ofMinutes(5));
    }

    static Ride ride() {
        final var id = RideId.generate();
        return Ride.request(id, RiderId.generate(), VehicleType.CAR, new Place(MONAS, "Jl. Merdeka Barat 12"),
                new Place(GeoMath.offset(MONAS, 3_700, 0), "Mall Kelapa Gading"), quote(VehicleType.CAR, T0), PaymentMethod.WALLET,
                PinHasher.hash(id.value(), PIN), WINDOW, T0);
    }

    static Ride assigned(UUID driver) {
        final var r = ride();
        r.offerTo(driver, 800, 180, TTL, T0.plusSeconds(1));
        r.acceptOffer(driver, T0.plusSeconds(5));
        return r;
    }

    static Ride arrived(UUID driver) {
        final var r = assigned(driver);
        r.markArrived(driver, GeoMath.offset(MONAS, 20, 0), T0.plusSeconds(200));
        return r;
    }

    static Ride inProgress(UUID driver) {
        final var r = arrived(driver);
        assertThat(r.start(driver, PIN, MONAS, T0.plusSeconds(260))).isEqualTo(Ride.StartResult.STARTED);
        return r;
    }

    /** Drives north in equal steps; returns the time of the last fix. */
    static Instant drive(Ride r, int steps, double metersPerStep, long secondsPerStep) {
        Instant t = r.startedAt();
        LatLng p = MONAS;
        for (int i = 0; i < steps; i++) {
            t = t.plusSeconds(secondsPerStep);
            p = GeoMath.offset(p, metersPerStep, 0);
            r.recordPosition(p, t);
        }
        return t;
    }

    // ---------------------------------------------------------------- request

    @Test
    void requestStartsSearchingAndEmitsOnlyCoarseLocation() {
        final var r = ride();
        assertThat(r.status()).isEqualTo(RideStatus.SEARCHING);
        final var dump = r.pullDomainEvents().toString();
        assertThat(dump).contains("mobility.ride.requested".isEmpty() ? "" : "pickupCell");
        assertThat(dump).doesNotContain("106.8272", "-6.1754", "Merdeka", "Kelapa", "pin");
    }

    @Test
    void invalidRequestsAreRejected() {
        final var id = RideId.generate();
        final var hash = PinHasher.hash(id.value(), PIN);
        final var far = new Place(GeoMath.offset(MONAS, 200_000, 0), "x");
        final var near = new Place(GeoMath.offset(MONAS, 50, 0), "x");
        final var ok = new Place(GeoMath.offset(MONAS, 3_000, 0), "x");
        assertThatThrownBy(() -> Ride.request(id, RiderId.generate(), VehicleType.CAR, new Place(MONAS, ""), near, quote(VehicleType.CAR, T0), PaymentMethod.CASH, hash, WINDOW, T0))
                .hasMessageContaining("too close");
        assertThatThrownBy(() -> Ride.request(id, RiderId.generate(), VehicleType.CAR, new Place(MONAS, ""), far, quote(VehicleType.CAR, T0), PaymentMethod.CASH, hash, WINDOW, T0))
                .hasMessageContaining("too long");
        assertThatThrownBy(() -> Ride.request(id, RiderId.generate(), VehicleType.CAR, new Place(MONAS, ""), ok, quote(VehicleType.CAR, T0), PaymentMethod.CASH, hash, WINDOW, T0.plusSeconds(301)))
                .hasMessageContaining("expired");
        assertThatThrownBy(() -> Ride.request(id, RiderId.generate(), VehicleType.CAR, new Place(MONAS, ""), ok, quote(VehicleType.BIKE, T0), PaymentMethod.CASH, hash, WINDOW, T0))
                .hasMessageContaining("different vehicle");
    }

    // ---------------------------------------------------------------- offers

    @Test
    void oneLiveOfferAtATimeAndOnlyThatDriverCanAccept() {
        final var r = ride();
        r.offerTo(D1, 800, 180, TTL, T0.plusSeconds(1));
        assertThatThrownBy(() -> r.offerTo(D2, 900, 200, TTL, T0.plusSeconds(2))).isInstanceOf(InvalidStateException.class);
        assertThatThrownBy(() -> r.acceptOffer(D2, T0.plusSeconds(3))).isInstanceOf(OfferExpiredException.class);   // not offered to D2
        r.acceptOffer(D1, T0.plusSeconds(4));
        assertThat(r.status()).isEqualTo(RideStatus.DRIVER_ASSIGNED);
        assertThat(r.driverId()).contains(D1);
        assertThatThrownBy(() -> r.acceptOffer(D1, T0.plusSeconds(5))).isInstanceOf(InvalidStateException.class); // cannot accept twice
    }

    @Test
    void lateAcceptanceIsRefusedAndTheSweeperExpiresTheOffer() {
        final var r = ride();
        r.offerTo(D1, 800, 180, TTL, T0.plusSeconds(1));
        assertThatThrownBy(() -> r.acceptOffer(D1, T0.plusSeconds(16))).isInstanceOf(OfferExpiredException.class);   // 15 s TTL ends at +16
        assertThat(r.expireOffer(T0.plusSeconds(10))).isEmpty();                                                      // not yet
        assertThat(r.expireOffer(T0.plusSeconds(16))).contains(D1);
        assertThat(r.needsOffer(T0.plusSeconds(17))).isTrue();
        assertThat(r.expireOffer(T0.plusSeconds(17))).isEmpty();                                                      // idempotent
    }

    @Test
    void declinedAndExpiredDriversAreNeverOfferedAgain() {
        final var r = ride();
        r.offerTo(D1, 800, 180, TTL, T0.plusSeconds(1)); r.declineOffer(D1, T0.plusSeconds(3));
        assertThatThrownBy(() -> r.offerTo(D1, 800, 180, TTL, T0.plusSeconds(4))).hasMessageContaining("already saw");
        r.offerTo(D2, 900, 200, TTL, T0.plusSeconds(4));
        r.expireOffer(T0.plusSeconds(30));
        assertThat(r.triedDrivers()).containsExactlyInAnyOrder(D1, D2);
        assertThatThrownBy(() -> r.offerTo(D2, 900, 200, TTL, T0.plusSeconds(31))).hasMessageContaining("already saw");
    }

    @Test
    void searchEndsAtTheDeadlineOrTheOfferLimit() {
        final var late = ride();
        assertThat(late.needsOffer(T0.plusSeconds(89))).isTrue();
        assertThat(late.needsOffer(T0.plusSeconds(90))).isFalse();
        assertThat(late.searchExhausted(T0.plusSeconds(90))).isTrue();
        assertThatThrownBy(() -> late.offerTo(D1, 1, 1, TTL, T0.plusSeconds(90))).hasMessageContaining("closed");
        late.markNoDrivers(T0.plusSeconds(90));
        assertThat(late.status()).isEqualTo(RideStatus.NO_DRIVERS);

        final var many = ride();
        for (int i = 0; i < Ride.MAX_OFFERS; i++) {
            many.offerTo(UUID.randomUUID(), 1, 1, Duration.ofSeconds(1), T0.plusSeconds(i));
            many.expireOffer(T0.plusSeconds(i + 1));
        }
        assertThat(many.searchExhausted(T0.plusSeconds(Ride.MAX_OFFERS - 1))).isTrue();
    }

    // ---------------------------------------------------------------- pickup

    @Test
    void arrivalIsGeofencedAndDriverBound() {
        final var r = assigned(D1);
        assertThatThrownBy(() -> r.markArrived(D1, GeoMath.offset(MONAS, 500, 0), T0.plusSeconds(100))).hasMessageContaining("not at the pickup");
        assertThatThrownBy(() -> r.markArrived(D2, MONAS, T0.plusSeconds(100))).hasMessageContaining("different driver");
        r.markArrived(D1, GeoMath.offset(MONAS, 100, 0), T0.plusSeconds(100));
        assertThat(r.status()).isEqualTo(RideStatus.DRIVER_ARRIVED);
    }

    @Test
    void pinStartsTheTripAndWrongAttemptsAreCountedThenLocked() {
        final var r = arrived(D1);
        assertThat(r.start(D1, "0000", MONAS, T0.plusSeconds(210))).isEqualTo(Ride.StartResult.WRONG_PIN);
        assertThat(r.pinAttempts()).isEqualTo(1);                                          // the failed attempt is recorded, not lost
        assertThat(r.snapshot().pinAttempts()).isEqualTo(1);
        for (int i = 0; i < 3; i++) r.start(D1, "1111", MONAS, T0.plusSeconds(211 + i));
        assertThat(r.start(D1, "2222", MONAS, T0.plusSeconds(220))).isEqualTo(Ride.StartResult.PIN_LOCKED);
        assertThat(r.start(D1, PIN, MONAS, T0.plusSeconds(221))).isEqualTo(Ride.StartResult.PIN_LOCKED);   // even the right PIN, once locked
        assertThat(r.status()).isEqualTo(RideStatus.DRIVER_ARRIVED);
    }

    @Test
    void rightPinStartsTheTripOnlyNearThePickup() {
        final var r = arrived(D1);
        assertThatThrownBy(() -> r.start(D1, PIN, GeoMath.offset(MONAS, 800, 0), T0.plusSeconds(210))).hasMessageContaining("not at the pickup");
        assertThat(r.start(D1, PIN, MONAS, T0.plusSeconds(210))).isEqualTo(Ride.StartResult.STARTED);
        assertThat(r.status()).isEqualTo(RideStatus.IN_PROGRESS);
    }

    @Test
    void pinHashingIsSaltedPerRideAndConstantTimeComparable() {
        final var a = UUID.randomUUID(); final var b = UUID.randomUUID();
        assertThat(PinHasher.hash(a, "1234")).isNotEqualTo(PinHasher.hash(b, "1234"));
        assertThat(PinHasher.matches(a, "1234", PinHasher.hash(a, "1234"))).isTrue();
        assertThat(PinHasher.matches(a, "1235", PinHasher.hash(a, "1234"))).isFalse();
        assertThat(PinHasher.matches(a, null, PinHasher.hash(a, "1234"))).isFalse();
    }

    // ---------------------------------------------------------------- trip tracking and fare

    @Test
    void trackingAccumulatesDistanceAndIgnoresGlitches() {
        final var r = inProgress(D1);
        final Instant end = drive(r, 4, 250, 30);
        assertThat(r.trackedMeters()).isBetween(990.0, 1010.0);
        assertThat(r.recordPosition(GeoMath.offset(r.lastPoint(), 50_000, 0), end.plusSeconds(5))).isFalse();      // teleport
        assertThat(r.recordPosition(GeoMath.offset(r.lastPoint(), 3, 3), end.plusSeconds(10))).isTrue();            // jitter: accepted, not counted
        assertThat(r.trackedMeters()).isBetween(990.0, 1010.0);
        assertThat(r.recordPosition(r.lastPoint(), end.minusSeconds(100))).isFalse();                              // out of order
        assertThat(assigned(D1).recordPosition(MONAS, T0.plusSeconds(50))).isFalse();                               // not in progress
    }

    @Test
    void tripCloseToTheEstimateKeepsTheUpfrontPrice() {
        final var r = inProgress(D1);
        final Instant end = drive(r, 10, 520, 95);                                                                 // 5.2 km, 950 s
        final var fare = r.complete(D1, S, end);
        assertThat(fare.basis()).isEqualTo(FinalFare.Basis.UPFRONT);
        assertThat(fare.total()).isEqualByComparingTo(r.quote().total());
        assertThat(r.status()).isEqualTo(RideStatus.COMPLETED);
        assertThat(r.pullDomainEvents()).extracting(e -> e.eventType()).contains("mobility.ride.completed");
    }

    @Test
    void bigDetourIsMetered() {
        final var r = inProgress(D1);
        final Instant end = drive(r, 16, 500, 60);                                                                 // 8 km, 960 s
        final var fare = r.complete(D1, S, end);
        assertThat(fare.basis()).isEqualTo(FinalFare.Basis.METERED);
        assertThat(fare.subtotal()).isEqualByComparingTo("39500");
    }

    @Test
    void unreliableTrackingFallsBackToTheUpfrontPrice() {
        final var r = inProgress(D1);                                                                              // no fixes at all (phone had no signal)
        final var fare = r.complete(D1, S, T0.plusSeconds(1_200));
        assertThat(fare.basis()).isEqualTo(FinalFare.Basis.UPFRONT);
        assertThat(fare.total()).isEqualByComparingTo(r.quote().total());
    }

    @Test
    void onlyTheAssignedDriverCanCompleteAnInProgressTrip() {
        final var r = inProgress(D1);
        assertThatThrownBy(() -> r.complete(D2, S, T0.plusSeconds(900))).hasMessageContaining("different driver");
        assertThatThrownBy(() -> assigned(D1).complete(D1, S, T0.plusSeconds(900))).isInstanceOf(InvalidStateException.class);
    }

    @Test
    void destinationChangeKeepsTheLockedSurgeAndNeedsAnAssignedDriver() {
        assertThatThrownBy(() -> ride().changeDestination(new Place(GeoMath.offset(MONAS, 4_000, 0), ""), quote(VehicleType.CAR, T0), T0)).isInstanceOf(InvalidStateException.class);
        final var r = inProgress(D1);
        final var surged = FareCalculator.quote(S, VehicleType.CAR, 7_000, 1_200, new BigDecimal("1.5"), null, T0, Duration.ofMinutes(5));
        assertThatThrownBy(() -> r.changeDestination(new Place(GeoMath.offset(MONAS, 5_000, 0), ""), surged, T0.plusSeconds(300))).hasMessageContaining("locked");
        final var newQuote = FareCalculator.quote(S, VehicleType.CAR, 7_000, 1_200, new BigDecimal("1.0"), null, T0.plusSeconds(300), Duration.ofMinutes(5));
        r.changeDestination(new Place(GeoMath.offset(MONAS, 5_000, 0), ""), newQuote, T0.plusSeconds(300));
        assertThat(r.quote().total()).isEqualByComparingTo(newQuote.total());
    }

    // ---------------------------------------------------------------- cancellation

    @Test
    void riderCancellationFeeDependsOnDriverAssignmentTiming() {
        final var searching = ride();
        assertThat(searching.cancelByRider("changed mind", POLICY, S, T0.plusSeconds(20))).isEqualByComparingTo("0");
        assertThat(searching.status()).isEqualTo(RideStatus.CANCELLED);

        final var quick = assigned(D1);                                                                            // assigned at T0+5
        assertThat(quick.cancelByRider("oops", POLICY, S, T0.plusSeconds(100))).isEqualByComparingTo("0");         // inside the 2-minute grace

        final var slow = assigned(D1);
        assertThat(slow.cancelByRider("late", POLICY, S, T0.plusSeconds(200))).isEqualByComparingTo("8000");
        final var evt = slow.pullDomainEvents().toString();
        assertThat(evt).contains("RIDER").contains("hadDriver=true");
    }

    @Test
    void ridersCannotCancelOnceTheTripHasStarted() {
        assertThatThrownBy(() -> inProgress(D1).cancelByRider("x", POLICY, S, T0.plusSeconds(400))).isInstanceOf(InvalidStateException.class);
    }

    @Test
    void noShowNeedsTheFullWaitAndChargesTheHigherFee() {
        final var r = arrived(D1);                                                                                 // arrived at T0+200
        assertThatThrownBy(() -> r.markNoShow(D1, POLICY, S, T0.plusSeconds(499))).hasMessageContaining("wait");
        assertThat(r.markNoShow(D1, POLICY, S, T0.plusSeconds(500))).isEqualByComparingTo("12000");
        assertThat(r.cancelledBy()).isEqualTo(CancelledBy.RIDER_NO_SHOW);
        assertThatThrownBy(() -> assigned(D1).markNoShow(D1, POLICY, S, T0.plusSeconds(900))).isInstanceOf(InvalidStateException.class);
    }

    @Test
    void driverWithdrawalSendsTheRideBackToSearchingWithoutChargingTheRider() {
        final var r = assigned(D1);
        assertThat(r.driverWithdraws(D1, "flat tyre", WINDOW, T0.plusSeconds(60))).isTrue();
        assertThat(r.status()).isEqualTo(RideStatus.SEARCHING);
        assertThat(r.driverId()).isEmpty();
        assertThat(r.searchDeadline()).isEqualTo(T0.plusSeconds(150));
        assertThatThrownBy(() -> r.offerTo(D1, 1, 1, TTL, T0.plusSeconds(61))).hasMessageContaining("already saw");
        r.offerTo(D2, 1, 1, TTL, T0.plusSeconds(61)); r.acceptOffer(D2, T0.plusSeconds(65));
        assertThat(r.driverId()).contains(D2);
        assertThat(r.pinAttempts()).isZero();
    }

    @Test
    void afterTwoReassignmentsTheRideIsCancelledBySystemForFree() {
        final var r = assigned(D1);
        r.driverWithdraws(D1, "x", WINDOW, T0.plusSeconds(10));
        r.offerTo(D2, 1, 1, TTL, T0.plusSeconds(11)); r.acceptOffer(D2, T0.plusSeconds(12));
        r.driverWithdraws(D2, "x", WINDOW, T0.plusSeconds(20));
        r.offerTo(D3, 1, 1, TTL, T0.plusSeconds(21)); r.acceptOffer(D3, T0.plusSeconds(22));
        assertThat(r.driverWithdraws(D3, "x", WINDOW, T0.plusSeconds(30))).isFalse();
        assertThat(r.status()).isEqualTo(RideStatus.CANCELLED);
        assertThat(r.cancelledBy()).isEqualTo(CancelledBy.SYSTEM);
        assertThat(r.cancelFee()).isEqualByComparingTo("0");
    }

    @Test
    void systemCancellationIsFreeAndOnlyForActiveRides() {
        final var r = assigned(D1);
        r.cancelBySystem("ops", T0.plusSeconds(500));
        assertThat(r.cancelFee()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> r.cancelBySystem("again", T0.plusSeconds(501))).isInstanceOf(InvalidStateException.class);
    }

    // ---------------------------------------------------------------- after the trip

    Ride completed() {
        final var r = inProgress(D1);
        r.complete(D1, S, drive(r, 10, 520, 95));
        return r;
    }

    @Test
    void ratingsAreOncePerSideWithinTheWindowAfterCompletion() {
        final var r = completed(); final Instant t = r.completedAt();
        assertThatThrownBy(() -> ride().rate(true, 5, T0)).hasMessageContaining("no longer be rated");
        r.rate(true, 5, t.plusSeconds(60)); r.rate(false, 4, t.plusSeconds(61));
        assertThatThrownBy(() -> r.rate(true, 1, t.plusSeconds(62))).hasMessageContaining("Already");
        assertThatThrownBy(() -> completed().rate(true, 6, T0.plusSeconds(2000))).hasMessageContaining("1-5");
        assertThatThrownBy(() -> r.rate(false, 3, t.plus(Duration.ofHours(25)))).hasMessageContaining("no longer");
    }

    @Test
    void tipIsCappedOncePerRideAndOnlyAfterCompletion() {
        final var r = completed(); final Instant t = r.completedAt();
        assertThatThrownBy(() -> r.addTip(new BigDecimal("999999"), t)).hasMessageContaining("Invalid tip");
        assertThatThrownBy(() -> r.addTip(BigDecimal.ZERO, t)).isInstanceOf(BusinessRuleViolation.class);
        r.addTip(new BigDecimal("5000"), t.plusSeconds(30));
        assertThat(r.tip()).isEqualByComparingTo("5000");
        assertThatThrownBy(() -> r.addTip(new BigDecimal("1000"), t.plusSeconds(60))).hasMessageContaining("Already");
        assertThatThrownBy(() -> inProgress(D1).addTip(new BigDecimal("1000"), T0)).isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    void sosOnlyDuringAnActiveTripAndIdempotent() {
        assertThatThrownBy(() -> ride().triggerSos(true, T0)).isInstanceOf(InvalidStateException.class);
        final var r = inProgress(D1); r.pullDomainEvents();
        r.triggerSos(true, T0.plusSeconds(300)); r.triggerSos(true, T0.plusSeconds(301));
        assertThat(r.pullDomainEvents()).extracting(e -> e.eventType()).containsExactly("mobility.ride.sos");
    }

    @Test
    void settlementFlagIsOneWay() {
        final var r = completed();
        assertThat(r.markSettled(r.completedAt())).isTrue();
        assertThat(r.markSettled(r.completedAt())).isFalse();
        final var freeCancel = ride(); freeCancel.cancelByRider("x", POLICY, S, T0.plusSeconds(5));
        assertThatThrownBy(() -> freeCancel.markSettled(T0)).isInstanceOf(InvalidStateException.class);          // nothing to settle
        final var feeCancel = assigned(D1); feeCancel.cancelByRider("late", POLICY, S, T0.plusSeconds(300));
        assertThat(feeCancel.markSettled(T0)).isTrue();                                                           // a cancellation fee does need settling
    }

    @Test
    void snapshotRoundTripKeepsTheWholeStory() {
        final var r = completed(); r.rate(true, 5, r.completedAt()); r.addTip(new BigDecimal("2000"), r.completedAt());
        final var back = Ride.restore(r.snapshot());
        assertThat(back.snapshot()).isEqualTo(r.snapshot());
        assertThat(back.status()).isEqualTo(RideStatus.COMPLETED);
        assertThat(back.offers()).hasSize(1);
        assertThat(back.pullDomainEvents()).isEmpty();
    }
}
