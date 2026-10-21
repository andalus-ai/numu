package tech.kayys.numou.mobility.integration;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.aggregate.memory.DispatchingEventSink;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.mobility.driver.adapter.memory.InMemoryDriverLocationIndex;
import tech.kayys.numou.mobility.driver.adapter.memory.InMemoryDriverRepository;
import tech.kayys.numou.mobility.driver.domain.Vehicle;
import tech.kayys.numou.mobility.kernel.domain.VehicleType;
import tech.kayys.numou.mobility.kernel.geo.GeoMath;
import tech.kayys.numou.mobility.kernel.geo.HaversineRouting;
import tech.kayys.numou.mobility.kernel.geo.LatLng;
import tech.kayys.numou.mobility.pricing.PaymentMethod;
import tech.kayys.numou.mobility.ride.adapter.memory.InMemoryMarket;
import tech.kayys.numou.mobility.ride.adapter.memory.InMemoryRideRepositories;
import tech.kayys.numou.mobility.ride.adapter.memory.RecordingNotifications;
import tech.kayys.numou.mobility.ride.application.Actor;
import tech.kayys.numou.mobility.ride.application.QuoteView;
import tech.kayys.numou.mobility.ride.application.RideView;
import tech.kayys.numou.mobility.wallet.adapter.memory.InMemoryWalletRepositories;
import tech.kayys.numou.mobility.wallet.domain.OwnerKind;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/** Everything wired with in-memory adapters exactly as production wires it with Postgres ones. A fresh instance per test. */
abstract class MobilityTestBase {

    static final LatLng MONAS = new LatLng(-6.1754, 106.8272);
    static final byte[] SECRET = "0123456789abcdef0123456789abcdef-test-secret".getBytes();

    final UUID tenant = UUID.randomUUID();
    final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-10T10:00:00Z"));
    final UnitOfWork uow = new UnitOfWork() { public <R> Uni<R> execute(java.util.function.Supplier<Uni<R>> w) { return w.get(); } };
    final DispatchingEventSink bus = new DispatchingEventSink();
    final RecordingNotifications push = new RecordingNotifications();
    final InMemoryRideRepositories.Rides rides = new InMemoryRideRepositories.Rides();
    final InMemoryRideRepositories.Locks locks = new InMemoryRideRepositories.Locks();
    final InMemoryDriverRepository driverRepo = new InMemoryDriverRepository();
    final InMemoryDriverLocationIndex index = new InMemoryDriverLocationIndex();
    final InMemoryWalletRepositories.Wallets walletRepo = new InMemoryWalletRepositories.Wallets();
    final InMemoryWalletRepositories.Ledger ledger = new InMemoryWalletRepositories.Ledger();
    final InMemoryMarket.Promos promos = new InMemoryMarket.Promos();
    final tech.kayys.numou.mobility.ride.adapter.memory.InMemoryTripTraces traces = new tech.kayys.numou.mobility.ride.adapter.memory.InMemoryTripTraces();
    final MobilityComposition.Services s;

    MobilityTestBase() {
        this(UnaryOperator.identity());
    }

    MobilityTestBase(UnaryOperator<tech.kayys.numou.mobility.ride.spi.PaymentsPort> paymentsDecorator) {
        final var config = MobilityComposition.Config.indonesiaAround(MONAS, 40_000, SECRET);
        final var ports = new MobilityComposition.Ports(rides, locks, driverRepo, index, walletRepo, ledger, new InMemoryMarket.Demand(),
                new InMemoryMarket.SurgeState(), promos, push, new HaversineRouting(), uow, bus, now::get, traces, new InMemoryRideRepositories.Chats());
        s = MobilityComposition.build(config, ports, paymentsDecorator);
        MobilityPolicies.register(bus, s.settlement());
    }

    // ---------------------------------------------------------------- fixtures

    static LatLng north(LatLng from, double meters) { return GeoMath.offset(from, meters, 0); }

    LatLng dropoff() { return north(MONAS, 3_700); }

    void advance(long seconds) { now.set(now.get().plusSeconds(seconds)); }

    UUID driver(VehicleType type, LatLng at) {
        final var id = UUID.randomUUID();
        s.drivers().register(tenant, id, "Driver " + id.toString().substring(0, 4), "+62 811 000 " + id.toString().substring(0, 4),
                new Vehicle(type, "B " + Math.abs(id.hashCode() % 9000 + 1000) + " XY", "Avanza", "silver")).await().indefinitely();
        s.drivers().approve(tenant, id).await().indefinitely();
        s.drivers().goOnline(tenant, id).await().indefinitely();
        ping(id, at);
        return id;
    }

    UUID carDriver(double metersNorthOfPickup) { return driver(VehicleType.CAR, north(MONAS, metersNorthOfPickup)); }

    void ping(UUID driver, LatLng at) {
        s.drivers().updateLocation(tenant, driver, at, 0, 0, now.get()).await().indefinitely();
    }

    UUID rider(long topUp) {
        final var id = UUID.randomUUID();
        if (topUp > 0) s.wallets().credit(tenant, OwnerKind.RIDER, id, BigDecimal.valueOf(topUp), "topup:" + id, "test").await().indefinitely();
        return id;
    }

    QuoteView quote(UUID rider, VehicleType v, String promo) {
        return s.rides().quote(tenant, rider, MONAS, dropoff(), Set.of(v), promo).await().indefinitely().get(0);
    }

    QuoteView carQuote(UUID rider) { return quote(rider, VehicleType.CAR, null); }

    RideView book(UUID rider, QuoteView q, PaymentMethod m) {
        return s.rides().request(tenant, rider, q.token(), m).await().indefinitely();
    }

    RideView riderView(UUID rider, UUID ride) { return s.rides().get(tenant, Actor.rider(rider), ride).await().indefinitely(); }

    RideView driverView(UUID driver, UUID ride) { return s.rides().get(tenant, Actor.driver(driver), ride).await().indefinitely(); }

    BigDecimal balance(OwnerKind kind, UUID owner) { return s.wallets().view(tenant, kind, owner).await().indefinitely().balance(); }

    BigDecimal held(OwnerKind kind, UUID owner) { return s.wallets().view(tenant, kind, owner).await().indefinitely().held(); }

    /** Accept, drive to the pickup, arrive and start with the rider's PIN. Leaves the trip IN_PROGRESS. */
    void pickUp(UUID rider, UUID driver, UUID ride) {
        s.dispatch().accept(tenant, driver, ride).await().indefinitely();
        advance(60);
        ping(driver, north(MONAS, 20));
        s.rides().arrive(tenant, driver, ride, north(MONAS, 20)).await().indefinitely();
        final String pin = riderView(rider, ride).pin();
        s.rides().start(tenant, driver, ride, pin, MONAS).await().indefinitely();
    }

    /** Drives {@code steps} equal legs north from the pickup, one fix per leg. */
    void driveNorth(UUID driver, int steps, double meters, long secondsPerStep) {
        for (int i = 1; i <= steps; i++) {
            advance(secondsPerStep);
            s.rides().trackPosition(tenant, driver, north(MONAS, i * meters), 0, 40, now.get()).await().indefinitely();
        }
    }
}
