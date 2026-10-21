package tech.kayys.numou.mobility.driver.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.mobility.kernel.domain.DriverId;
import tech.kayys.numou.mobility.kernel.domain.RideId;
import tech.kayys.numou.mobility.kernel.domain.VehicleType;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DriverDomainTest {

    static final Instant T = Instant.parse("2026-10-10T10:00:00Z");

    static Driver approved() {
        final var d = Driver.register(DriverId.generate(), "Agus Wijaya", "+62 811 2222 3333", new Vehicle(VehicleType.CAR, "b 1234 xyz", "Avanza", "silver"), T);
        d.approve(T);
        return d;
    }

    @Test
    void onlyApprovedDriversCanGoOnline() {
        final var d = Driver.register(DriverId.generate(), "A", "+62 811 1", new Vehicle(VehicleType.BIKE, "B 1 A", "", ""), T);
        assertThatThrownBy(() -> d.goOnline(T)).isInstanceOf(DriverUnavailableException.class);
        d.approve(T); d.goOnline(T);
        assertThat(d.status()).isEqualTo(Driver.Status.ONLINE);
        assertThat(d.isDispatchable(T)).isTrue();
    }

    @Test
    void platesAreNormalised() {
        assertThat(approved().vehicle().plate()).isEqualTo("B 1234 XYZ");
    }

    @Test
    void claimTakesTheDriverOffThePoolAndSecondClaimFails() {
        final var d = approved(); d.goOnline(T);
        final var r1 = RideId.generate(); final var r2 = RideId.generate();
        d.claim(r1, T);
        assertThat(d.status()).isEqualTo(Driver.Status.ON_TRIP);
        assertThat(d.isDispatchable(T)).isFalse();
        d.claim(r1, T);                                                      // same ride again: idempotent
        assertThatThrownBy(() -> d.claim(r2, T)).isInstanceOf(DriverUnavailableException.class);
    }

    @Test
    void offlineDriversCannotBeClaimed() {
        final var d = approved();
        assertThatThrownBy(() -> d.claim(RideId.generate(), T)).isInstanceOf(DriverUnavailableException.class);
    }

    @Test
    void releaseOnlyForTheCurrentRide() {
        final var d = approved(); d.goOnline(T);
        final var r1 = RideId.generate();
        d.claim(r1, T);
        assertThat(d.release(RideId.generate(), T)).isFalse();               // late event for another ride
        assertThat(d.status()).isEqualTo(Driver.Status.ON_TRIP);
        assertThat(d.release(r1, T)).isTrue();
        assertThat(d.release(r1, T)).isFalse();                              // duplicate
        assertThat(d.status()).isEqualTo(Driver.Status.ONLINE);
    }

    @Test
    void cannotGoOfflineMidTrip() {
        final var d = approved(); d.goOnline(T); d.claim(RideId.generate(), T);
        assertThatThrownBy(() -> d.goOffline(T)).isInstanceOf(InvalidStateException.class);
    }

    @Test
    void threeCancellationsInADaySuspendTheDriverForTwoHours() {
        final var d = approved(); d.goOnline(T); d.pullDomainEvents();
        d.recordCancellation(T); d.recordCancellation(T.plusSeconds(600));
        assertThat(d.isSuspended(T.plusSeconds(700))).isFalse();
        d.recordCancellation(T.plusSeconds(1200));
        assertThat(d.isSuspended(T.plusSeconds(1300))).isTrue();
        assertThat(d.isDispatchable(T.plusSeconds(1300))).isFalse();
        assertThat(d.pullDomainEvents()).extracting(e -> e.eventType()).contains("mobility.driver.suspended");
        d.goOffline(T.plusSeconds(1400));
        assertThatThrownBy(() -> d.goOnline(T.plusSeconds(1500))).isInstanceOf(DriverUnavailableException.class);
        d.goOnline(T.plusSeconds(1200).plus(Driver.SUSPENSION).plusSeconds(1));
        assertThat(d.status()).isEqualTo(Driver.Status.ONLINE);
    }

    @Test
    void oldCancellationsDoNotCount() {
        final var d = approved();
        d.recordCancellation(T); d.recordCancellation(T.plusSeconds(60));
        d.recordCancellation(T.plus(Duration.ofHours(25)));                  // the first two fell out of the 24h window
        assertThat(d.isSuspended(T.plus(Duration.ofHours(25)))).isFalse();
    }

    @Test
    void ratingAndAcceptanceAreNeutralUntilThereIsHistory() {
        final var d = approved();
        assertThat(d.rating()).isEqualTo(4.8);
        assertThat(d.acceptanceRate()).isEqualTo(1.0);
        for (int i = 0; i < 5; i++) d.recordRating(i < 3 ? 5 : 3, T);
        assertThat(d.rating()).isEqualTo(4.2);                               // (5+5+5+3+3)/5
        for (int i = 0; i < 10; i++) d.recordOffer(i < 4, T);
        assertThat(d.acceptanceRate()).isEqualTo(0.4);
        assertThatThrownBy(() -> d.recordRating(6, T)).isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    void rejectedDriversMustBeOffline() {
        final var d = approved(); d.goOnline(T);
        assertThatThrownBy(() -> d.reject("bad documents", T)).isInstanceOf(InvalidStateException.class);
    }

    @Test
    void snapshotRoundTripKeepsEverything() {
        final var d = approved(); d.goOnline(T); d.claim(RideId.generate(), T); d.recordRating(5, T); d.recordCancellation(T);
        final var back = Driver.restore(d.snapshot());
        assertThat(back.snapshot()).isEqualTo(d.snapshot());
        assertThat(back.pullDomainEvents()).isEmpty();
    }
}
