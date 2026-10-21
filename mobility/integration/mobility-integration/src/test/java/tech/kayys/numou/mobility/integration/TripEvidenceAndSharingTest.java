package tech.kayys.numou.mobility.integration;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.mobility.kernel.domain.RideId;
import tech.kayys.numou.mobility.kernel.geo.GeoMath;
import tech.kayys.numou.mobility.kernel.geo.Polyline;
import tech.kayys.numou.mobility.pricing.PaymentMethod;
import tech.kayys.numou.mobility.ride.application.Actor;
import tech.kayys.numou.mobility.ride.application.TripShareTokens;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TripEvidenceAndSharingTest extends MobilityTestBase {

    @Test
    void normalTrackingWritesTheTraceButNeverRewritesTheRideDocument() {
        final UUID rider = rider(200_000); final UUID d = carDriver(-300);
        final var booked = book(rider, carQuote(rider), PaymentMethod.WALLET);
        pickUp(rider, d, booked.id());
        final long versionBefore = rides.find(tenant, RideId.of(booked.id())).await().indefinitely().orElseThrow().storedVersion();
        driveNorth(d, 8, 462.5, 100);                                                       // eight GPS fixes
        final long versionAfter = rides.find(tenant, RideId.of(booked.id())).await().indefinitely().orElseThrow().storedVersion();
        assertThat(versionAfter).as("no document writes while driving on route").isEqualTo(versionBefore);
        assertThat(traces.load(tenant, RideId.of(booked.id())).await().indefinitely()).hasSize(9);   // start point + 8 fixes
    }

    @Test
    void theFinalDistanceAndFareComeFromTheTrace() {
        final UUID rider = rider(300_000); final UUID d = carDriver(-300);
        final var q = carQuote(rider);
        final var booked = book(rider, q, PaymentMethod.WALLET);
        pickUp(rider, d, booked.id());
        driveNorth(d, 16, 500, 60);                                                          // a detour: 8 km instead of ~5
        final var done = s.rides().complete(tenant, d, booked.id()).await().indefinitely();
        assertThat(done.finalTotal()).isGreaterThan(q.total());                              // metered from the trace
        final var ride = rides.find(tenant, RideId.of(booked.id())).await().indefinitely().orElseThrow();
        assertThat(ride.trackedMeters()).isBetween(7_950.0, 8_050.0);
    }

    @Test
    void theRouteIsAvailableAsASimplifiedPolylineToTheRiderTheDriverAndOpsOnly() {
        final UUID rider = rider(200_000); final UUID d = carDriver(-300);
        final var booked = book(rider, carQuote(rider), PaymentMethod.WALLET);
        pickUp(rider, d, booked.id());
        driveNorth(d, 8, 462.5, 100);
        s.rides().complete(tenant, d, booked.id()).await().indefinitely();
        final var view = s.rides().trace(tenant, Actor.rider(rider), booked.id()).await().indefinitely();
        assertThat(view.recordedPoints()).isEqualTo(9);
        assertThat(view.shownPoints()).isEqualTo(2);                                         // a straight road is just its two ends
        final var path = Polyline.decode(view.polyline());
        assertThat(path.get(path.size() - 1).distanceTo(GeoMath.offset(MONAS, 3_700, 0))).isLessThan(5.0);
        assertThat(view.distanceMeters()).isBetween(3_690.0, 3_710.0);
        assertThat(s.rides().trace(tenant, Actor.driver(d), booked.id()).await().indefinitely().recordedPoints()).isEqualTo(9);
        assertThat(s.rides().trace(tenant, Actor.ops(UUID.randomUUID()), booked.id()).await().indefinitely().recordedPoints()).isEqualTo(9);
        assertThatThrownBy(() -> s.rides().trace(tenant, Actor.rider(UUID.randomUUID()), booked.id()).await().indefinitely())
                .isInstanceOf(tech.kayys.numou.aggregate.domain.NotFoundException.class);
        assertThatThrownBy(() -> s.rides().trace(tenant, Actor.driver(UUID.randomUUID()), booked.id()).await().indefinitely())
                .isInstanceOf(tech.kayys.numou.aggregate.domain.NotFoundException.class);
    }

    @Test
    void drivingFarOffTheRouteRaisesOneDeviationEventAndPersistsTheStreak() {
        final UUID rider = rider(200_000); final UUID d = carDriver(-300);
        final var booked = book(rider, carQuote(rider), PaymentMethod.WALLET);
        pickUp(rider, d, booked.id());
        for (int i = 1; i <= 5; i++) {
            advance(60);
            s.rides().trackPosition(tenant, d, GeoMath.offset(MONAS, 1_000, i * 1_000.0), 0, 60, now.get()).await().indefinitely();   // heading east, far from a north-south trip
        }
        assertThat(bus.types().stream().filter(t -> t.equals("mobility.ride.route-deviated"))).hasSize(1);
        assertThat(rides.find(tenant, RideId.of(booked.id())).await().indefinitely().orElseThrow().offRouteStreak()).isGreaterThanOrEqualTo(3);
    }

    // ---------------------------------------------------------------- share links

    @Test
    void aShareLinkShowsALiveButMinimalViewWithoutAnAccount() {
        final UUID rider = rider(200_000); final UUID d = carDriver(-300);
        final var booked = book(rider, carQuote(rider), PaymentMethod.WALLET);
        assertThatThrownBy(() -> s.rides().shareLink(tenant, rider, booked.id(), Duration.ofHours(1)).await().indefinitely())
                .isInstanceOf(BusinessRuleViolation.class);                                  // nothing to share until a driver is assigned
        pickUp(rider, d, booked.id());
        driveNorth(d, 3, 462.5, 100);
        final String link = s.rides().shareLink(tenant, rider, booked.id(), Duration.ofHours(2)).await().indefinitely();

        final var view = s.rides().publicTracking(link).await().indefinitely();
        assertThat(view.status()).isEqualTo("IN_PROGRESS");
        assertThat(view.ended()).isFalse();
        assertThat(view.driverFirstName()).isEqualTo("Driver");
        assertThat(view.plate()).isNotBlank();
        assertThat(view.lat()).isNotNull();
        final String dump = view.toString();
        final String pin = new tech.kayys.numou.mobility.ride.application.RidePins(SECRET).pinFor(booked.id());   // the PIN is hidden from rider views once the trip has started
        assertThat(pin).matches("\\d{4}");
        assertThat(dump).doesNotContain("+62 811").doesNotContain("pin=" + pin);           // no phone, no PIN
        assertThat(java.util.Arrays.stream(view.getClass().getRecordComponents()).map(c -> c.getName()))
                .doesNotContain("pin", "pickup", "dropoff", "fare", "total", "riderName", "phone");
    }

    @Test
    void shareLinksCannotBeForgedStolenForAnotherRideOrUsedAfterExpiry() {
        final UUID rider = rider(200_000); final UUID d = carDriver(-300);
        final var booked = book(rider, carQuote(rider), PaymentMethod.WALLET);
        pickUp(rider, d, booked.id());
        final String link = s.rides().shareLink(tenant, rider, booked.id(), Duration.ofMinutes(30)).await().indefinitely();
        final String forged = link.substring(0, 12) + (link.charAt(12) == 'A' ? 'B' : 'A') + link.substring(13);
        assertThatThrownBy(() -> s.rides().publicTracking(forged).await().indefinitely()).hasMessageContaining("invalid or has expired");
        assertThatThrownBy(() -> s.rides().publicTracking("garbage").await().indefinitely()).hasMessageContaining("invalid or has expired");
        assertThatThrownBy(() -> s.rides().shareLink(tenant, UUID.randomUUID(), booked.id(), Duration.ofHours(1)).await().indefinitely())
                .isInstanceOf(tech.kayys.numou.aggregate.domain.NotFoundException.class);   // only the rider can create a link
        final String quoteToken = carQuote(rider).token();
        assertThatThrownBy(() -> s.rides().publicTracking(quoteToken).await().indefinitely()).hasMessageContaining("invalid");   // a quote token is not a share link
        advance(31 * 60);
        assertThatThrownBy(() -> s.rides().publicTracking(link).await().indefinitely()).hasMessageContaining("invalid or has expired");
    }

    @Test
    void afterTheTripTheLinkShowsOnlyTheFinalStatusForAnHour() {
        final UUID rider = rider(200_000); final UUID d = carDriver(-300);
        final var booked = book(rider, carQuote(rider), PaymentMethod.WALLET);
        pickUp(rider, d, booked.id());
        driveNorth(d, 8, 462.5, 100);
        final String link = s.rides().shareLink(tenant, rider, booked.id(), Duration.ofHours(6)).await().indefinitely();
        s.rides().complete(tenant, d, booked.id()).await().indefinitely();
        final var ended = s.rides().publicTracking(link).await().indefinitely();
        assertThat(ended.ended()).isTrue();
        assertThat(ended.status()).isEqualTo("COMPLETED");
        assertThat(ended.lat()).isNull();                                                    // no live position once the trip is over
        advance(3_601);
        assertThatThrownBy(() -> s.rides().publicTracking(link).await().indefinitely()).hasMessageContaining("invalid or has expired");
    }

    @Test
    void tokenWithTheWrongSecretIsRejected() {
        final var other = new TripShareTokens("zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz-other".getBytes());
        final var token = other.issue(new TripShareTokens.Claims(tenant, UUID.randomUUID(), now.get().plusSeconds(600)));
        assertThatThrownBy(() -> s.rides().publicTracking(token).await().indefinitely()).hasMessageContaining("invalid");
    }
}
