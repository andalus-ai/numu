package tech.kayys.numou.mobility.integration;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.mobility.kernel.domain.RideId;
import tech.kayys.numou.mobility.kernel.domain.VehicleType;
import tech.kayys.numou.mobility.pricing.PaymentMethod;
import tech.kayys.numou.mobility.pricing.Promo;
import tech.kayys.numou.mobility.ride.application.Actor;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatAndRiskTest extends MobilityTestBase {

    UUID assignedRide(UUID rider, UUID driver) {
        final var booked = book(rider, carQuote(rider), PaymentMethod.WALLET);
        s.dispatch().accept(tenant, driver, booked.id()).await().indefinitely();
        return booked.id();
    }

    // ---------------------------------------------------------------- chat

    @Test
    void ridersAndDriversChatOnlyWithEachOtherAndNumbersAreHidden() {
        final UUID rider = rider(100_000); final UUID d = carDriver(-300);
        final UUID ride = assignedRide(rider, d);
        final var sent = s.chat().send(tenant, Actor.rider(rider), ride, "I'm at the blue gate, call 0812 3456 7890").await().indefinitely();
        assertThat(sent.text()).isEqualTo("I'm at the blue gate, call [number hidden]");
        advance(2);
        s.chat().send(tenant, Actor.driver(d), ride, "Coming, 2 minutes").await().indefinitely();
        assertThat(s.chat().history(tenant, Actor.rider(rider), ride, null).await().indefinitely()).extracting(m -> m.sender()).containsExactly("RIDER", "DRIVER");
        assertThat(s.chat().history(tenant, Actor.driver(d), ride, now.get().minusSeconds(1)).await().indefinitely()).hasSize(1);   // only newer than the cursor
        assertThat(push.to(d)).extracting(x -> x.detail()).contains("CHAT_MESSAGE");
        assertThat(push.to(rider)).extracting(x -> x.detail()).contains("CHAT_MESSAGE");
        assertThat(bus.all().toString()).doesNotContain("blue gate", "Coming");                                                   // text never reaches events
    }

    @Test
    void outsidersCannotReadOrWriteAndOtherTenantsSeeNothing() {
        final UUID rider = rider(100_000); final UUID d = carDriver(-300); final UUID other = carDriver(-900);
        final UUID ride = assignedRide(rider, d);
        for (final Actor stranger : new Actor[]{Actor.rider(UUID.randomUUID()), Actor.driver(other), Actor.ops(UUID.randomUUID())}) {
            assertThatThrownBy(() -> s.chat().send(tenant, stranger, ride, "hi").await().indefinitely()).isInstanceOf(tech.kayys.numou.aggregate.domain.NotFoundException.class);
            assertThatThrownBy(() -> s.chat().history(tenant, stranger, ride, null).await().indefinitely()).isInstanceOf(tech.kayys.numou.aggregate.domain.NotFoundException.class);
        }
        assertThatThrownBy(() -> s.chat().send(UUID.randomUUID(), Actor.rider(rider), ride, "hi").await().indefinitely()).isInstanceOf(tech.kayys.numou.aggregate.domain.NotFoundException.class);
    }

    @Test
    void chatIsClosedWhileSearchingOpenDuringTheRideAndForThirtyMinutesAfter() {
        final UUID rider = rider(100_000); final UUID d = carDriver(-300);
        final var booked = book(rider, carQuote(rider), PaymentMethod.WALLET);                        // still searching: no driver to talk to
        assertThatThrownBy(() -> s.chat().send(tenant, Actor.rider(rider), booked.id(), "hello?").await().indefinitely()).hasMessageContaining("not available");
        pickUp(rider, d, booked.id());
        s.chat().send(tenant, Actor.rider(rider), booked.id(), "on my way out").await().indefinitely();
        driveNorth(d, 8, 462.5, 100);
        s.rides().complete(tenant, d, booked.id()).await().indefinitely();
        advance(20 * 60);
        s.chat().send(tenant, Actor.rider(rider), booked.id(), "did I leave my bag?").await().indefinitely();     // 20 min after: still open
        advance(11 * 60);
        assertThatThrownBy(() -> s.chat().send(tenant, Actor.driver(d), booked.id(), "no sorry").await().indefinitely()).hasMessageContaining("not available");
        assertThat(s.chat().history(tenant, Actor.rider(rider), booked.id(), null).await().indefinitely()).hasSize(2);        // history stays readable
    }

    @Test
    void floodingIsRefused() {
        final UUID rider = rider(100_000); final UUID d = carDriver(-300);
        final UUID ride = assignedRide(rider, d);
        s.chat().send(tenant, Actor.rider(rider), ride, "one").await().indefinitely();
        assertThatThrownBy(() -> s.chat().send(tenant, Actor.rider(rider), ride, "two").await().indefinitely()).hasMessageContaining("too quickly");
    }

    // ---------------------------------------------------------------- fraud signals

    UUID completedWith(UUID rider, UUID d) {
        advance(120); ping(d, north(MONAS, -300));
        final var booked = book(rider, carQuote(rider), PaymentMethod.WALLET);
        pickUp(rider, d, booked.id());
        driveNorth(d, 8, 462.5, 100);
        s.rides().complete(tenant, d, booked.id()).await().indefinitely();
        return booked.id();
    }

    @Test
    void theSameRiderAndDriverRidingTogetherRepeatedlyIsFlaggedForOps() {
        final UUID rider = rider(1_000_000); final UUID d = carDriver(-300);
        for (int i = 0; i < 3; i++) completedWith(rider, d);
        assertThat(bus.types()).doesNotContain("mobility.ride.flagged");                              // three rides together: not yet
        completedWith(rider, d);                                                                       // the 4th within a week
        assertThat(bus.types().stream().filter(t -> t.equals("mobility.ride.flagged"))).hasSize(1);
        assertThat(bus.all().stream().filter(p -> p.event().eventType().equals("mobility.ride.flagged")).findFirst().orElseThrow().event().toString()).contains("PAIR_REPEAT");
    }

    @Test
    void anOrdinaryRideIsNeverFlagged() {
        final UUID rider = rider(200_000); final UUID d = carDriver(-300);
        final var ride = completedWith(rider, d);
        final var a = s.risk().assessCompleted(tenant, rides.find(tenant, RideId.of(ride)).await().indefinitely().orElseThrow()).await().indefinitely();
        assertThat(a.score()).isZero();
        assertThat(a.flagged()).isFalse();
        assertThat(bus.types()).doesNotContain("mobility.ride.flagged");
    }

    @Test
    void aDiscountedShortTripWithNoMovementIsFlaggedWithItsReasons() {
        promos.add(tenant, new Promo("FREEBIE", Promo.Type.PERCENT, new BigDecimal("50"), new BigDecimal("10000"), BigDecimal.ZERO, now.get().minusSeconds(60), now.get().plusSeconds(86_400)));
        final UUID rider = rider(100_000); final UUID d = carDriver(-300);
        final var q = s.rides().quote(tenant, rider, MONAS, north(MONAS, 800), Set.of(VehicleType.CAR), "FREEBIE").await().indefinitely().get(0);
        final var booked = book(rider, q, PaymentMethod.WALLET);
        pickUp(rider, d, booked.id());                                                                 // the phone then reports no movement at all
        s.rides().complete(tenant, d, booked.id()).await().indefinitely();
        final var a = s.risk().assessCompleted(tenant, rides.find(tenant, RideId.of(booked.id())).await().indefinitely().orElseThrow()).await().indefinitely();
        assertThat(a.reasons()).containsExactlyInAnyOrder("PROMO_SHORT_TRIP", "NO_MOVEMENT");
        assertThat(a.score()).isEqualTo(80);
        assertThat(a.flagged()).isTrue();
        assertThat(bus.types()).contains("mobility.ride.flagged");
    }
}
