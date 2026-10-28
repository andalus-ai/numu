package tech.kayys.numou.mobility.integration;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.mobility.driver.domain.DriverUnavailableException;
import tech.kayys.numou.mobility.kernel.domain.RiderId;
import tech.kayys.numou.mobility.kernel.domain.VehicleType;
import tech.kayys.numou.mobility.kernel.geo.GeoMath;
import tech.kayys.numou.mobility.kernel.geo.LatLng;
import tech.kayys.numou.mobility.pricing.PaymentMethod;
import tech.kayys.numou.mobility.pricing.Promo;
import tech.kayys.numou.mobility.ride.application.RideExceptions;
import tech.kayys.numou.mobility.ride.domain.ActiveRideExistsException;
import tech.kayys.numou.mobility.ride.domain.OfferExpiredException;
import tech.kayys.numou.wallet.domain.InsufficientFundsException;
import tech.kayys.numou.wallet.domain.OwnerKind;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RideDispatchAndEdgeCasesTest extends MobilityTestBase {

    static BigDecimal bd(String s) { return new BigDecimal(s); }

    // ---------------------------------------------------------------- matching

    @Test
    void aDeclinedOfferGoesStraightToTheNextDriver() {
        final UUID rider = rider(100_000); final UUID d1 = carDriver(-300); final UUID d2 = carDriver(-900);
        final var booked = book(rider, carQuote(rider), PaymentMethod.WALLET);
        assertThat(push.to(d1)).extracting(x -> x.kind()).contains("offer");
        s.dispatch().decline(tenant, d1, booked.id()).await().indefinitely();
        assertThat(push.to(d2)).extracting(x -> x.kind()).contains("offer");
        s.dispatch().accept(tenant, d2, booked.id()).await().indefinitely();
        assertThat(riderView(rider, booked.id()).driver().id()).isEqualTo(d2);
    }

    @Test
    void anUnansweredOfferExpiresAndTheSweeperMovesOnWhileALateAcceptIsRefused() {
        final UUID rider = rider(100_000); final UUID d1 = carDriver(-300); final UUID d2 = carDriver(-900);
        final var booked = book(rider, carQuote(rider), PaymentMethod.WALLET);
        advance(16);
        ping(d1, north(MONAS, -300)); ping(d2, north(MONAS, -900));
        final var sweep = s.dispatch().sweep(tenant).await().indefinitely();
        assertThat(sweep.offered()).isEqualTo(1);
        assertThat(push.to(d1)).extracting(x -> x.kind()).contains("offer-withdrawn");
        assertThat(push.to(d2)).extracting(x -> x.kind()).contains("offer");
        assertThatThrownBy(() -> s.dispatch().accept(tenant, d1, booked.id()).await().indefinitely()).isInstanceOf(OfferExpiredException.class);
        s.dispatch().accept(tenant, d2, booked.id()).await().indefinitely();
    }

    @Test
    void whenNobodyAcceptsInTimeTheSearchEndsAndEverythingIsReleased() {
        final UUID rider = rider(100_000);
        final var booked = book(rider, carQuote(rider), PaymentMethod.WALLET);                    // no drivers at all
        assertThat(held(OwnerKind.RIDER, rider)).isPositive();
        advance(91);
        assertThat(s.dispatch().sweep(tenant).await().indefinitely().noDrivers()).isEqualTo(1);
        assertThat(riderView(rider, booked.id()).status()).isEqualTo("NO_DRIVERS");
        assertThat(held(OwnerKind.RIDER, rider)).isEqualByComparingTo("0");
        assertThat(balance(OwnerKind.RIDER, rider)).isEqualByComparingTo("100000");
        assertThat(push.to(rider)).extracting(x -> x.detail()).contains("NO_DRIVERS");
        advance(1);
        book(rider, carQuote(rider), PaymentMethod.WALLET);                                      // the lock was released: rider can try again
    }

    @Test
    void rankingPrefersReliableDriversOverMerelyNearOnes() {
        final UUID rider = rider(100_000);
        final UUID nearButFlaky = carDriver(-300); final UUID farButReliable = carDriver(-600);
        for (int i = 0; i < 10; i++) s.drivers().recordOffer(tenant, nearButFlaky, i < 4).await().indefinitely();     // accepts 40%
        final var booked = book(rider, carQuote(rider), PaymentMethod.WALLET);
        assertThat(push.to(farButReliable)).extracting(x -> x.kind()).contains("offer");
        assertThat(push.to(nearButFlaky)).isEmpty();
        assertThat(booked.status()).isEqualTo("SEARCHING");
    }

    @Test
    void cashRidesSkipDriversWhoseCommissionDebtIsAtTheLimit() {
        final UUID rider = rider(0);
        final UUID indebted = carDriver(-200); final UUID fine = carDriver(-900);
        s.wallets().capture(tenant, OwnerKind.DRIVER, indebted, bd("100000"), "none", "debt", "owed commission").await().indefinitely();
        book(rider, carQuote(rider), PaymentMethod.CASH);
        assertThat(push.to(indebted)).isEmpty();
        assertThat(push.to(fine)).extracting(x -> x.kind()).contains("offer");
    }

    @Test
    void driversOfAnotherVehicleTypeAreNeverOffered() {
        final UUID rider = rider(100_000);
        final UUID bike = driver(VehicleType.BIKE, north(MONAS, -100));
        book(rider, carQuote(rider), PaymentMethod.WALLET);
        assertThat(push.to(bike)).isEmpty();
    }

    // ---------------------------------------------------------------- cancellations

    @Test
    void cancellingWhileSearchingOrQuicklyAfterAssignmentIsFree() {
        final UUID rider = rider(100_000); final UUID d = carDriver(-300);
        final var first = book(rider, carQuote(rider), PaymentMethod.WALLET);
        s.rides().cancelByRider(tenant, rider, first.id(), "changed mind").await().indefinitely();
        assertThat(balance(OwnerKind.RIDER, rider)).isEqualByComparingTo("100000");
        assertThat(held(OwnerKind.RIDER, rider)).isEqualByComparingTo("0");

        final var second = book(rider, carQuote(rider), PaymentMethod.WALLET);
        s.dispatch().accept(tenant, d, second.id()).await().indefinitely();
        advance(60);
        final var cancelled = s.rides().cancelByRider(tenant, rider, second.id(), "oops").await().indefinitely();
        assertThat(cancelled.cancellationFee()).isEqualByComparingTo("0");
        assertThat(balance(OwnerKind.RIDER, rider)).isEqualByComparingTo("100000");
        assertThat(s.drivers().get(tenant, d).await().indefinitely().status()).isEqualTo("ONLINE");                  // driver freed
    }

    @Test
    void aLateCancellationChargesTheFeeAndSharesItWithTheDriver() {
        final UUID rider = rider(100_000); final UUID d = carDriver(-300);
        final var booked = book(rider, carQuote(rider), PaymentMethod.WALLET);
        s.dispatch().accept(tenant, d, booked.id()).await().indefinitely();
        advance(130);
        final var cancelled = s.rides().cancelByRider(tenant, rider, booked.id(), "too slow").await().indefinitely();
        assertThat(cancelled.cancellationFee()).isEqualByComparingTo("8000");
        assertThat(balance(OwnerKind.RIDER, rider)).isEqualByComparingTo("92000");
        assertThat(held(OwnerKind.RIDER, rider)).isEqualByComparingTo("0");
        assertThat(balance(OwnerKind.DRIVER, d)).isEqualByComparingTo("6400");
        assertThat(s.wallets().platformBalance(tenant).await().indefinitely()).isEqualByComparingTo("1600");
    }

    @Test
    void aRiderWhoNeverShowsPaysTheNoShowFeeAfterTheDriverHasWaited() {
        final UUID rider = rider(100_000); final UUID d = carDriver(-300);
        final var booked = book(rider, carQuote(rider), PaymentMethod.WALLET);
        s.dispatch().accept(tenant, d, booked.id()).await().indefinitely();
        advance(60); ping(d, north(MONAS, 10));
        s.rides().arrive(tenant, d, booked.id(), north(MONAS, 10)).await().indefinitely();
        advance(200);
        assertThatThrownBy(() -> s.rides().noShow(tenant, d, booked.id()).await().indefinitely()).hasMessageContaining("wait");
        advance(101);
        s.rides().noShow(tenant, d, booked.id()).await().indefinitely();
        assertThat(balance(OwnerKind.RIDER, rider)).isEqualByComparingTo("88000");
        assertThat(balance(OwnerKind.DRIVER, d)).isEqualByComparingTo("9600");
        assertThat(s.drivers().get(tenant, d).await().indefinitely().status()).isEqualTo("ONLINE");
    }

    @Test
    void whenAssignedDriversKeepWithdrawingTheRideIsReMatchedTwiceThenCancelledForFree() {
        final UUID rider = rider(100_000);
        final UUID d1 = carDriver(-300), d2 = carDriver(-600), d3 = carDriver(-900);
        final var booked = book(rider, carQuote(rider), PaymentMethod.WALLET);

        s.dispatch().accept(tenant, d1, booked.id()).await().indefinitely();
        s.rides().driverCancel(tenant, d1, booked.id(), "tyre").await().indefinitely();
        assertThat(riderView(rider, booked.id()).status()).isEqualTo("SEARCHING");
        assertThat(push.to(d2)).extracting(x -> x.kind()).contains("offer");                      // matched again immediately

        s.dispatch().accept(tenant, d2, booked.id()).await().indefinitely();
        s.rides().driverCancel(tenant, d2, booked.id(), "emergency").await().indefinitely();
        s.dispatch().accept(tenant, d3, booked.id()).await().indefinitely();
        s.rides().driverCancel(tenant, d3, booked.id(), "no").await().indefinitely();             // third withdrawal: give up

        final var end = riderView(rider, booked.id());
        assertThat(end.status()).isEqualTo("CANCELLED");
        assertThat(end.cancellationFee()).isEqualByComparingTo("0");
        assertThat(balance(OwnerKind.RIDER, rider)).isEqualByComparingTo("100000");
        assertThat(held(OwnerKind.RIDER, rider)).isEqualByComparingTo("0");
        for (final UUID d : List.of(d1, d2, d3)) assertThat(s.drivers().get(tenant, d).await().indefinitely().status()).isEqualTo("ONLINE");
    }

    // ---------------------------------------------------------------- pickup safety

    @Test
    void wrongPinAttemptsArePersistedAndLockTheTripAfterFive() {
        final UUID rider = rider(100_000); final UUID d = carDriver(-300);
        final var booked = book(rider, carQuote(rider), PaymentMethod.WALLET);
        s.dispatch().accept(tenant, d, booked.id()).await().indefinitely();
        advance(60); ping(d, north(MONAS, 10));
        s.rides().arrive(tenant, d, booked.id(), north(MONAS, 10)).await().indefinitely();
        final String pin = riderView(rider, booked.id()).pin();
        final String wrong = pin.equals("0000") ? "1111" : "0000";

        assertThatThrownBy(() -> s.rides().start(tenant, d, booked.id(), wrong, MONAS).await().indefinitely())
                .isInstanceOfSatisfying(RideExceptions.WrongPinException.class, e -> assertThat(e.attemptsLeft()).isEqualTo(4));
        assertThat(rides.find(tenant, tech.kayys.numou.mobility.kernel.domain.RideId.of(booked.id())).await().indefinitely().orElseThrow().pinAttempts()).isEqualTo(1);
        for (int i = 0; i < 3; i++) assertThatThrownBy(() -> s.rides().start(tenant, d, booked.id(), wrong, MONAS).await().indefinitely()).isInstanceOf(RideExceptions.WrongPinException.class);
        assertThatThrownBy(() -> s.rides().start(tenant, d, booked.id(), wrong, MONAS).await().indefinitely()).isInstanceOf(RideExceptions.PinLockedException.class);
        assertThatThrownBy(() -> s.rides().start(tenant, d, booked.id(), pin, MONAS).await().indefinitely()).isInstanceOf(RideExceptions.PinLockedException.class);
        assertThat(riderView(rider, booked.id()).status()).isEqualTo("DRIVER_ARRIVED");
    }

    @Test
    void aDriverCannotArriveFromFarAwayOrOnSomeoneElsesRide() {
        final UUID rider = rider(100_000); final UUID d = carDriver(-300); final UUID other = carDriver(-1_500);
        final var booked = book(rider, carQuote(rider), PaymentMethod.WALLET);
        s.dispatch().accept(tenant, d, booked.id()).await().indefinitely();
        assertThatThrownBy(() -> s.rides().arrive(tenant, d, booked.id(), north(MONAS, -2_000)).await().indefinitely()).hasMessageContaining("not at the pickup");
        assertThatThrownBy(() -> s.rides().arrive(tenant, other, booked.id(), MONAS).await().indefinitely()).isInstanceOf(tech.kayys.numou.aggregate.domain.NotFoundException.class);
    }

    // ---------------------------------------------------------------- quotes

    @Test
    void tamperedStolenOrExpiredQuotesAreRefused() {
        final UUID rider = rider(100_000);
        final var q = carQuote(rider);
        final String t = q.token();
        final String forged = t.substring(0, 10) + (t.charAt(10) == 'A' ? 'B' : 'A') + t.substring(11);
        assertThatThrownBy(() -> s.rides().request(tenant, rider, forged, PaymentMethod.WALLET).await().indefinitely()).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> s.rides().request(tenant, rider(100_000), t, PaymentMethod.WALLET).await().indefinitely()).hasMessageContaining("Invalid quote");   // someone else's token
        assertThatThrownBy(() -> s.rides().request(UUID.randomUUID(), rider, t, PaymentMethod.WALLET).await().indefinitely()).hasMessageContaining("Invalid quote");  // another tenant
        assertThatThrownBy(() -> s.rides().request(tenant, rider, "garbage", PaymentMethod.WALLET).await().indefinitely()).hasMessageContaining("Invalid quote");
        advance(301);
        assertThatThrownBy(() -> s.rides().request(tenant, rider, t, PaymentMethod.WALLET).await().indefinitely()).hasMessageContaining("expired");
    }

    @Test
    void pickupsOutsideTheServiceAreaAreRefused() {
        final var far = GeoMath.offset(MONAS, 100_000, 0);
        assertThatThrownBy(() -> s.rides().quote(tenant, UUID.randomUUID(), far, north(far, 3_000), Set.of(VehicleType.CAR), null).await().indefinitely())
                .isInstanceOf(RideExceptions.OutsideServiceAreaException.class);
        assertThatThrownBy(() -> s.rides().quote(tenant, UUID.randomUUID(), MONAS, north(MONAS, 50), Set.of(VehicleType.CAR), null).await().indefinitely())
                .hasMessageContaining("200 m");
    }

    @Test
    void quotesListEveryRequestedVehicleCheapestBikeFirstByEnumOrder() {
        final var quotes = s.rides().quote(tenant, UUID.randomUUID(), MONAS, dropoff(), Set.of(VehicleType.CAR, VehicleType.BIKE, VehicleType.CAR_XL), null).await().indefinitely();
        assertThat(quotes).extracting(q -> q.vehicle()).containsExactly("BIKE", "CAR", "CAR_XL");
        assertThat(quotes.get(0).total()).isLessThan(quotes.get(1).total());
        assertThat(quotes.get(1).total()).isLessThan(quotes.get(2).total());
    }

    // ---------------------------------------------------------------- one active ride, wallets

    @Test
    void aRiderCanHaveOnlyOneActiveRideEvenWhenTheyDoubleTapConcurrently() throws Exception {
        for (int round = 0; round < 8; round++) {
            final var t = new RideDispatchAndEdgeCasesTest();                       // fresh world per round
            final UUID rider = t.rider(500_000);
            final var q = t.carQuote(rider);
            final int taps = 8;
            final var pool = Executors.newFixedThreadPool(taps); final var go = new CountDownLatch(1);
            final List<Future<Object>> results = new ArrayList<>();
            for (int i = 0; i < taps; i++) {
                results.add(pool.submit(() -> {
                    go.await();
                    try { return t.s.rides().request(t.tenant, rider, q.token(), PaymentMethod.WALLET).await().indefinitely(); }
                    catch (ActiveRideExistsException e) { return e; }
                }));
            }
            go.countDown();
            int booked = 0;
            for (var f : results) if (f.get(15, TimeUnit.SECONDS) instanceof tech.kayys.numou.mobility.ride.application.RideView) booked++;
            pool.shutdown();
            assertThat(booked).as("round %d: rides booked", round).isEqualTo(1);
            assertThat(t.s.rides().history(t.tenant, rider, 10).await().indefinitely()).hasSize(1);
            // the losers' holds were all given back: only one ride's hold remains
            assertThat(t.held(OwnerKind.RIDER, rider)).as("round %d: held", round)
                    .isEqualByComparingTo(q.total().multiply(new BigDecimal("1.2")).setScale(0, java.math.RoundingMode.CEILING));
        }
    }

    @Test
    void aSecondBookingIsRefusedWhileOneIsActiveButAllowedAfterItEnds() {
        final UUID rider = rider(500_000);
        final var first = book(rider, carQuote(rider), PaymentMethod.WALLET);
        assertThatThrownBy(() -> book(rider, carQuote(rider), PaymentMethod.WALLET)).isInstanceOf(ActiveRideExistsException.class);
        assertThat(held(OwnerKind.RIDER, rider)).isEqualByComparingTo(held(OwnerKind.RIDER, rider));
        s.rides().cancelByRider(tenant, rider, first.id(), "x").await().indefinitely();
        book(rider, carQuote(rider), PaymentMethod.WALLET);
    }

    @Test
    void anInsufficientBalanceIsRefusedWithoutLeavingAHoldOrALock() {
        final UUID rider = rider(1_000);
        assertThatThrownBy(() -> book(rider, carQuote(rider), PaymentMethod.WALLET)).isInstanceOf(InsufficientFundsException.class);
        assertThat(held(OwnerKind.RIDER, rider)).isEqualByComparingTo("0");
        assertThat(book(rider, carQuote(rider), PaymentMethod.CASH).status()).isEqualTo("SEARCHING");   // no stale lock blocks a cash ride
    }

    @Test
    void ridersInDebtMustSettleBeforeBookingAgain() {
        final UUID rider = rider(100_000);
        s.wallets().capture(tenant, OwnerKind.RIDER, rider, bd("150000"), "none", "unpaid fare", "debt").await().indefinitely();
        assertThat(balance(OwnerKind.RIDER, rider)).isEqualByComparingTo("-50000");
        assertThatThrownBy(() -> book(rider, carQuote(rider), PaymentMethod.WALLET)).hasMessageContaining("top up");
    }

    // ---------------------------------------------------------------- promos and surge

    @Test
    void aPromoIsPaidByThePlatformNotTheDriverIsOncePerRiderAndRefundedOnCancel() {
        promos.add(tenant, new Promo("WELCOME", Promo.Type.PERCENT, bd("20"), bd("10000"), BigDecimal.ZERO, now.get().minusSeconds(3600), now.get().plusSeconds(86_400)));
        final UUID rider = rider(200_000); final UUID d = carDriver(-300);
        final var q = quote(rider, VehicleType.CAR, "welcome");
        assertThat(q.promoStatus()).isEqualTo("APPLIED");
        assertThat(q.discount()).isPositive();

        final var first = book(rider, q, PaymentMethod.WALLET);
        s.rides().cancelByRider(tenant, rider, first.id(), "x").await().indefinitely();                 // free cancel: the promo is given back
        final var second = book(rider, quote(rider, VehicleType.CAR, "WELCOME"), PaymentMethod.WALLET);

        pickUp(rider, d, second.id());
        driveNorth(d, 8, 462.5, 100);
        s.rides().complete(tenant, d, second.id()).await().indefinitely();
        final var qa = quote(rider, VehicleType.CAR, "WELCOME");
        final BigDecimal commission = qa.subtotal().multiply(new BigDecimal("0.20")).setScale(0, java.math.RoundingMode.HALF_UP);
        assertThat(balance(OwnerKind.DRIVER, d)).isEqualByComparingTo(qa.subtotal().subtract(commission));    // the promo did not reduce the driver's earning
        assertThat(s.wallets().platformBalance(tenant).await().indefinitely())
                .isEqualByComparingTo(commission.add(qa.bookingFee()).subtract(qa.discount()));               // platform = commission + booking fee - promo

        assertThatThrownBy(() -> book(rider, quote(rider, VehicleType.CAR, "WELCOME"), PaymentMethod.WALLET)).hasMessageContaining("already used");
    }

    @Test
    void surgeRampsInStepsFromDemandAndTheBookedPriceIsLocked() {
        final UUID watcher = rider(0);
        final var base = carQuote(watcher);
        for (int i = 0; i < 6; i++) { final UUID r = rider(0); book(r, carQuote(r), PaymentMethod.CASH); }   // 6 requests in the cell, no drivers
        advance(31);
        final var step1 = carQuote(watcher);
        advance(31);
        final var step2 = carQuote(watcher);
        assertThat(base.surgeMultiplier()).isEqualByComparingTo("1.0");
        assertThat(step1.surgeMultiplier()).isEqualByComparingTo("1.3");
        assertThat(step2.surgeMultiplier()).isEqualByComparingTo("1.6");
        assertThat(step2.total()).isGreaterThan(step1.total());
        assertThat(step1.total()).isGreaterThan(base.total());
        final UUID r = rider(0);
        final var shown = carQuote(r);
        advance(5);                                                                                      // still inside the 30 s step: same multiplier
        assertThat(book(r, shown, PaymentMethod.CASH).quotedTotal()).isEqualByComparingTo(shown.total());   // price shown is price charged
        assertThat(shown.surgeMultiplier()).isEqualByComparingTo("1.6");
    }

    // ---------------------------------------------------------------- races

    @Test
    void aDoubleTappedAcceptIsHarmlessAndAssignsExactlyOnce() throws Exception {
        for (int round = 0; round < 10; round++) {
            final var t = new RideDispatchAndEdgeCasesTest();
            final UUID rider = t.rider(100_000); final UUID d = t.carDriver(-300);
            final var booked = t.book(rider, t.carQuote(rider), PaymentMethod.WALLET);
            final int taps = 8;
            final var pool = Executors.newFixedThreadPool(taps); final var go = new CountDownLatch(1);
            final List<Future<?>> fs = new ArrayList<>();
            for (int i = 0; i < taps; i++) fs.add(pool.submit(() -> { go.await(); t.s.dispatch().accept(t.tenant, d, booked.id()).await().indefinitely(); return null; }));
            go.countDown();
            for (var f : fs) f.get(15, TimeUnit.SECONDS);                      // every duplicate succeeds: none fails, none frees the driver
            pool.shutdown();
            assertThat(t.bus.types().stream().filter(x -> x.equals("mobility.ride.assigned"))).as("round %d", round).hasSize(1);
            assertThat(t.s.drivers().get(t.tenant, d).await().indefinitely().status()).isEqualTo("ON_TRIP");
            assertThat(t.riderView(rider, booked.id()).status()).isEqualTo("DRIVER_ASSIGNED");
        }
    }

    @Test
    void whenOneDriverIsOfferedTwoRidesAndAcceptsBothOnlyOneSucceeds() throws Exception {
        for (int round = 0; round < 10; round++) {
            final var t = new RideDispatchAndEdgeCasesTest();
            final UUID r1 = t.rider(100_000), r2 = t.rider(100_000); final UUID d = t.carDriver(-300);
            final var ride1 = t.book(r1, t.carQuote(r1), PaymentMethod.WALLET);
            final var ride2 = t.book(r2, t.carQuote(r2), PaymentMethod.WALLET);        // the same single driver is offered both
            final var pool = Executors.newFixedThreadPool(2); final var go = new CountDownLatch(1);
            final List<Future<Boolean>> res = new ArrayList<>();
            for (final var ride : List.of(ride1, ride2)) {
                res.add(pool.submit(() -> {
                    go.await();
                    try { t.s.dispatch().accept(t.tenant, d, ride.id()).await().indefinitely(); return true; }
                    catch (DriverUnavailableException e) { return false; }
                }));
            }
            go.countDown();
            int wins = 0;
            for (var f : res) if (f.get(15, TimeUnit.SECONDS)) wins++;
            pool.shutdown();
            assertThat(wins).as("round %d", round).isEqualTo(1);
            assertThat(t.s.drivers().get(t.tenant, d).await().indefinitely().status()).isEqualTo("ON_TRIP");
            final long assigned = List.of(ride1, ride2).stream().filter(r -> {
                final var uid = r.id();
                final var who = (uid.equals(ride1.id()) ? r1 : r2);
                return t.riderView(who, uid).status().equals("DRIVER_ASSIGNED");
            }).count();
            assertThat(assigned).isEqualTo(1);
        }
    }

    // ---------------------------------------------------------------- tracking, safety, destination

    @Test
    void gpsGlitchesDoNotInflateTheFare() {
        final UUID rider = rider(200_000); final UUID d = carDriver(-300);
        final var booked = book(rider, carQuote(rider), PaymentMethod.WALLET);
        pickUp(rider, d, booked.id());
        advance(30);
        final var outcome = s.rides().trackPosition(tenant, d, north(MONAS, 60_000), 0, 0, now.get()).await().indefinitely();   // teleport
        assertThat(outcome).isEqualTo(tech.kayys.numou.mobility.driver.application.DriverService.LocationOutcome.REJECTED_IMPLAUSIBLE);
        driveNorth(d, 8, 462.5, 100);
        final var done = s.rides().complete(tenant, d, booked.id()).await().indefinitely();
        assertThat(done.finalTotal()).isEqualByComparingTo(carQuote(rider).total());
    }

    @Test
    void sosIsRaisedByEitherPartyOnlyDuringAnActiveTrip() {
        final UUID rider = rider(100_000); final UUID d = carDriver(-300);
        final var booked = book(rider, carQuote(rider), PaymentMethod.WALLET);
        assertThatThrownBy(() -> s.rides().sos(tenant, tech.kayys.numou.mobility.ride.application.Actor.rider(rider), booked.id()).await().indefinitely())
                .isInstanceOf(tech.kayys.numou.foundation.domain.exception.InvalidStateException.class);   // still searching: no trip yet
        pickUp(rider, d, booked.id());
        assertThat(s.rides().sos(tenant, tech.kayys.numou.mobility.ride.application.Actor.rider(rider), booked.id()).await().indefinitely().sos()).isTrue();
        assertThatThrownBy(() -> s.rides().sos(tenant, tech.kayys.numou.mobility.ride.application.Actor.rider(UUID.randomUUID()), booked.id()).await().indefinitely())
                .isInstanceOf(tech.kayys.numou.aggregate.domain.NotFoundException.class);
        assertThat(bus.types()).contains("mobility.ride.sos");
    }

    @Test
    void changingTheDestinationReQuotesAtTheLockedSurge() {
        final UUID rider = rider(300_000); final UUID d = carDriver(-300);
        final var booked = book(rider, carQuote(rider), PaymentMethod.WALLET);
        pickUp(rider, d, booked.id());
        final var changed = s.rides().changeDestination(tenant, rider, booked.id(), north(MONAS, 8_000)).await().indefinitely();
        assertThat(changed.quotedTotal()).isGreaterThan(booked.quotedTotal());
        assertThat(changed.dropoff().lat()).isEqualTo(north(MONAS, 8_000).lat());
    }

    @Test
    void theDriverAppCanPollForItsCurrentOfferUntilItExpires() {
        final UUID rider = rider(100_000); final UUID d1 = carDriver(-300); final UUID d2 = carDriver(-900);
        final var booked = book(rider, carQuote(rider), PaymentMethod.WALLET);
        final var offer = s.rides().currentOffer(tenant, d1).await().indefinitely();
        assertThat(offer).isPresent();
        assertThat(offer.get().id()).isEqualTo(booked.id());
        assertThat(offer.get().pickup().exact()).isFalse();                                   // coarse until accepted
        assertThat(s.rides().currentOffer(tenant, d2).await().indefinitely()).isEmpty();      // not offered to d2
        advance(16);
        assertThat(s.rides().currentOffer(tenant, d1).await().indefinitely()).isEmpty();      // lapsed, even before the sweeper runs
        s.dispatch().sweep(tenant).await().indefinitely();
        ping(d2, north(MONAS, -900));
        assertThat(s.rides().currentOffer(tenant, d2).await().indefinitely()).isPresent();
    }

    @Test
    void opsCanCancelAnActiveRideForFreeWhichFreesTheDriverAndReturnsTheHold() {
        final UUID rider = rider(100_000); final UUID d = carDriver(-300);
        final var booked = book(rider, carQuote(rider), PaymentMethod.WALLET);
        pickUp(rider, d, booked.id());
        final var cancelled = s.rides().cancelBySystem(tenant, booked.id(), "safety review").await().indefinitely();
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.cancellationFee()).isEqualByComparingTo("0");
        assertThat(cancelled.pin()).isNull();                                              // the ops view never shows the PIN
        assertThat(balance(OwnerKind.RIDER, rider)).isEqualByComparingTo("100000");
        assertThat(held(OwnerKind.RIDER, rider)).isEqualByComparingTo("0");
        assertThat(s.drivers().get(tenant, d).await().indefinitely().status()).isEqualTo("ONLINE");
    }

    @Test
    void addressLabelsAreSignedIntoTheQuoteAndShownOnlyToTheRiderAndTheAssignedDriver() {
        final UUID rider = rider(100_000); final UUID d = carDriver(-300);
        final var q = s.rides().quote(tenant, rider, MONAS, dropoff(), "Jl. Merdeka Barat 12 | Gedung A\n", "Mall Kelapa Gading", Set.of(VehicleType.CAR), null).await().indefinitely().get(0);
        final var booked = book(rider, q, PaymentMethod.WALLET);
        assertThat(booked.pickup().label()).isEqualTo("Jl. Merdeka Barat 12 | Gedung A");        // control characters trimmed; the pipe is harmless (encoded)
        assertThat(booked.dropoff().label()).isEqualTo("Mall Kelapa Gading");
        assertThat(driverView(d, booked.id()).pickup().label()).isEmpty();                          // merely offered: no address yet
        s.dispatch().accept(tenant, d, booked.id()).await().indefinitely();
        assertThat(driverView(d, booked.id()).dropoff().label()).isEqualTo("Mall Kelapa Gading");   // assigned: the driver needs it to navigate
        assertThat(bus.all().toString()).doesNotContain("Merdeka", "Kelapa");                        // never in events
    }

    @Test
    void aLabelCannotBeChangedAfterTheQuoteWasIssued() {
        final UUID rider = rider(100_000);
        final var q = s.rides().quote(tenant, rider, MONAS, dropoff(), "Home", "Work", Set.of(VehicleType.CAR), null).await().indefinitely().get(0);
        final String[] parts = q.token().split("\\.");
        final String forged = new String(java.util.Base64.getUrlDecoder().decode(parts[0])).replace("SG9tZQ", "RXZpbA");   // "Home" -> "Evil" in the base64 label
        final String tampered = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(forged.getBytes()) + "." + parts[1];
        assertThatThrownBy(() -> s.rides().request(tenant, rider, tampered, PaymentMethod.WALLET).await().indefinitely()).hasMessageContaining("Invalid quote");
    }
}
