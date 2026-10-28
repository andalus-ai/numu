package tech.kayys.numou.mobility.integration;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.mobility.kernel.domain.RideId;
import tech.kayys.numou.mobility.kernel.domain.VehicleType;
import tech.kayys.numou.mobility.pricing.PaymentMethod;
import tech.kayys.numou.mobility.ride.application.Actor;
import tech.kayys.numou.mobility.ride.domain.event.RideCompleted;
import tech.kayys.numou.wallet.domain.OwnerKind;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RideJourneyTest extends MobilityTestBase {

    @Test
    void aWalletRideFromBookingToSettlementConservesEveryRupiah() {
        final UUID rider = rider(200_000);
        final UUID near = carDriver(-800);                     // 800 m from the pickup
        final UUID far = driver(VehicleType.CAR, tech.kayys.numou.mobility.kernel.geo.GeoMath.offset(MONAS, 0, 1_500));
        final var quote = carQuote(rider);
        assertThat(quote.pickupEtaSeconds()).isPositive();

        // ---- book: the nearest driver is offered the ride
        final var booked = book(rider, quote, PaymentMethod.WALLET);
        assertThat(booked.status()).isEqualTo("SEARCHING");
        assertThat(push.to(near)).extracting(x -> x.kind()).contains("offer");
        assertThat(push.to(far)).isEmpty();
        assertThat(held(OwnerKind.RIDER, rider)).isEqualByComparingTo(quote.total().multiply(new BigDecimal("1.2")).setScale(0, java.math.RoundingMode.CEILING));

        // ---- the offered driver sees only coarse places, the rider's view has the PIN
        final var offered = driverView(near, booked.id());
        assertThat(offered.pickup().exact()).isFalse();
        assertThat(offered.pickup().label()).isEmpty();
        assertThat(offered.pin()).isNull();

        s.dispatch().accept(tenant, near, booked.id()).await().indefinitely();
        final var assigned = riderView(rider, booked.id());
        assertThat(assigned.status()).isEqualTo("DRIVER_ASSIGNED");
        assertThat(assigned.pin()).matches("\\d{4}");
        assertThat(assigned.driver().id()).isEqualTo(near);
        assertThat(assigned.driver().plate()).isNotBlank();
        assertThat(assigned.toString()).doesNotContain("+62 811");              // the driver's phone number is never shown to riders
        final var driverSees = driverView(near, booked.id());
        assertThat(driverSees.pickup().exact()).isTrue();
        assertThat(driverSees.pin()).isNull();                                   // ...and the driver never sees the PIN

        // ---- arrive, start with the PIN, drive, complete
        advance(60); ping(near, north(MONAS, 20));
        s.rides().arrive(tenant, near, booked.id(), north(MONAS, 20)).await().indefinitely();
        s.rides().start(tenant, near, booked.id(), assigned.pin(), MONAS).await().indefinitely();
        driveNorth(near, 8, 462.5, 100);
        final var done = s.rides().complete(tenant, near, booked.id()).await().indefinitely();
        assertThat(done.status()).isEqualTo("COMPLETED");
        assertThat(done.finalTotal()).isEqualByComparingTo(quote.total());       // within tolerance: the upfront price stands

        // ---- money: rider paid exactly the fare; driver + platform received exactly that; nothing created or lost
        final BigDecimal total = quote.total();
        final BigDecimal subtotal = quote.subtotal();
        final BigDecimal commission = subtotal.multiply(new BigDecimal("0.20")).setScale(0, java.math.RoundingMode.HALF_UP);
        final BigDecimal driverEarns = subtotal.subtract(commission);
        assertThat(balance(OwnerKind.RIDER, rider)).isEqualByComparingTo(new BigDecimal("200000").subtract(total));
        assertThat(held(OwnerKind.RIDER, rider)).isEqualByComparingTo("0");
        assertThat(balance(OwnerKind.DRIVER, near)).isEqualByComparingTo(driverEarns);
        final BigDecimal platform = s.wallets().platformBalance(tenant).await().indefinitely();
        assertThat(platform).isEqualByComparingTo(total.subtract(driverEarns));
        assertThat(driverEarns.add(platform)).isEqualByComparingTo(total);

        // ---- everything is freed: the driver is back in the pool and the rider can book again
        assertThat(s.drivers().get(tenant, near).await().indefinitely().status()).isEqualTo("ONLINE");
        assertThat(index.nearby(tenant, dropoff(), 500, VehicleType.CAR, now.get().minusSeconds(30), 5).await().indefinitely())   // the driver is where the trip ended
                .extracting(n -> n.fix().driverId().value()).contains(near);
        assertThat(locks.find(tenant, tech.kayys.numou.mobility.kernel.domain.RiderId.of(rider)).await().indefinitely().orElseThrow().holder()).isEmpty();

        // ---- settlement is idempotent: inline + event + manual re-runs change nothing
        final BigDecimal riderAfter = balance(OwnerKind.RIDER, rider);
        final int ledgerLines = ledger.size();
        s.settlement().settle(tenant, RideId.of(booked.id())).await().indefinitely();
        s.settlement().settle(tenant, RideId.of(booked.id())).await().indefinitely();
        assertThat(s.settlement().sweepUnsettled(tenant).await().indefinitely()).isZero();
        assertThat(balance(OwnerKind.RIDER, rider)).isEqualByComparingTo(riderAfter);
        assertThat(ledger.size()).isEqualTo(ledgerLines);

        // ---- after the trip: ratings feed driver quality; a tip goes 100% to the driver
        s.rides().rateAsRider(tenant, rider, booked.id(), 5).await().indefinitely();
        s.rides().rateAsDriver(tenant, near, booked.id(), 4).await().indefinitely();
        assertThat(s.drivers().get(tenant, near).await().indefinitely().rating()).isEqualTo(4.8);   // neutral until 5 ratings exist
        final BigDecimal driverBeforeTip = balance(OwnerKind.DRIVER, near);
        s.rides().tip(tenant, rider, booked.id(), new BigDecimal("3000")).await().indefinitely();
        assertThat(balance(OwnerKind.DRIVER, near)).isEqualByComparingTo(driverBeforeTip.add(new BigDecimal("3000")));
        assertThat(balance(OwnerKind.RIDER, rider)).isEqualByComparingTo(riderAfter.subtract(new BigDecimal("3000")));

        assertThat(bus.types()).contains("mobility.ride.requested", "mobility.ride.offered", "mobility.ride.assigned", "mobility.ride.driver-arrived",
                "mobility.ride.started", "mobility.ride.completed", "mobility.ride.rated", "mobility.ride.tipped");
        assertThat(bus.all().stream().filter(p -> p.event() instanceof RideCompleted)).hasSize(1);
        assertThat(bus.all()).allMatch(p -> p.tenantId().equals(tenant));
        assertThat(bus.all().toString()).doesNotContain("106.8272", "-6.1754", "+62 811");          // events carry no coordinates, phones or PINs
    }

    @Test
    void aCashRideChargesTheDriverTheirCommissionAndLeavesTheRiderWalletAlone() {
        final UUID rider = rider(0);
        final UUID driver = carDriver(-500);
        final var quote = carQuote(rider);
        final var booked = book(rider, quote, PaymentMethod.CASH);
        pickUp(rider, driver, booked.id());
        driveNorth(driver, 8, 462.5, 100);
        s.rides().complete(tenant, driver, booked.id()).await().indefinitely();

        final BigDecimal commission = quote.subtotal().multiply(new BigDecimal("0.20")).setScale(0, java.math.RoundingMode.HALF_UP);
        final BigDecimal owed = quote.total().subtract(quote.subtotal().subtract(commission));        // cash collected minus what the driver is owed
        assertThat(balance(OwnerKind.RIDER, rider)).isEqualByComparingTo("0");
        assertThat(balance(OwnerKind.DRIVER, driver)).isEqualByComparingTo(owed.negate());
        assertThat(s.wallets().platformBalance(tenant).await().indefinitely()).isEqualByComparingTo(owed);
    }

    @Test
    void aDetourIsMeteredAndStillSettlesExactly() {
        final UUID rider = rider(300_000);
        final UUID driver = carDriver(-500);
        final var quote = carQuote(rider);
        final var booked = book(rider, quote, PaymentMethod.WALLET);
        pickUp(rider, driver, booked.id());
        driveNorth(driver, 16, 500, 60);                                                          // 8 km instead of ~5 km
        final var done = s.rides().complete(tenant, driver, booked.id()).await().indefinitely();
        assertThat(done.finalTotal()).isGreaterThan(quote.total());
        assertThat(done.finalTotal()).isLessThanOrEqualTo(quote.total().multiply(new BigDecimal("1.5")));   // rider protection cap
        assertThat(balance(OwnerKind.RIDER, rider)).isEqualByComparingTo(new BigDecimal("300000").subtract(done.finalTotal()));
        final BigDecimal driverGot = balance(OwnerKind.DRIVER, driver);
        assertThat(driverGot.add(s.wallets().platformBalance(tenant).await().indefinitely())).isEqualByComparingTo(done.finalTotal());
    }

    @Test
    void rideHistoryAndOwnership() {
        final UUID rider = rider(100_000); final UUID driver = carDriver(-500);
        final var booked = book(rider, carQuote(rider), PaymentMethod.WALLET);
        assertThat(s.rides().history(tenant, rider, 10).await().indefinitely()).extracting(v -> v.id()).containsExactly(booked.id());
        assertThat(s.rides().history(tenant, UUID.randomUUID(), 10).await().indefinitely()).isEmpty();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> riderView(UUID.randomUUID(), booked.id())).isInstanceOf(tech.kayys.numou.aggregate.domain.NotFoundException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> driverView(UUID.randomUUID(), booked.id())).isInstanceOf(tech.kayys.numou.aggregate.domain.NotFoundException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> s.rides().get(UUID.randomUUID(), Actor.rider(rider), booked.id()).await().indefinitely())
                .isInstanceOf(tech.kayys.numou.aggregate.domain.NotFoundException.class);                 // another tenant sees nothing
        assertThat(driverView(driver, booked.id()).pickup().exact()).isFalse();                              // offered driver: coarse only
    }
}
