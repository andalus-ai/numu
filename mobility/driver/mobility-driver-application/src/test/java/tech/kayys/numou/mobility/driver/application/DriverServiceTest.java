package tech.kayys.numou.mobility.driver.application;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.aggregate.domain.NotFoundException;
import tech.kayys.numou.aggregate.memory.RecordingEventSink;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.mobility.driver.adapter.memory.InMemoryDriverLocationIndex;
import tech.kayys.numou.mobility.driver.adapter.memory.InMemoryDriverRepository;
import tech.kayys.numou.mobility.driver.application.DriverService.LocationOutcome;
import tech.kayys.numou.mobility.driver.domain.DriverUnavailableException;
import tech.kayys.numou.mobility.driver.domain.Vehicle;
import tech.kayys.numou.mobility.driver.spi.DriverLocationIndex;
import tech.kayys.numou.mobility.kernel.domain.DriverId;
import tech.kayys.numou.mobility.kernel.domain.RideId;
import tech.kayys.numou.mobility.kernel.domain.VehicleType;
import tech.kayys.numou.mobility.kernel.geo.GeoMath;
import tech.kayys.numou.mobility.kernel.geo.LatLng;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DriverServiceTest {

    static final LatLng MONAS = new LatLng(-6.1754, 106.8272);
    final UUID tenant = UUID.randomUUID();
    final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-10T10:00:00Z"));
    final UnitOfWork uow = new UnitOfWork() { public <R> Uni<R> execute(java.util.function.Supplier<Uni<R>> w) { return w.get(); } };
    final RecordingEventSink sink = new RecordingEventSink();
    InMemoryDriverLocationIndex index;
    DriverService svc;

    @BeforeEach
    void setUp() {
        index = new InMemoryDriverLocationIndex();
        svc = new DriverService(new InMemoryDriverRepository(), index, uow, sink, now::get);
    }

    UUID onlineDriver(VehicleType type) {
        final var id = UUID.randomUUID();
        svc.register(tenant, id, "Driver", "+62 811 000 111", new Vehicle(type, "B 1 X", "", "")).await().indefinitely();
        svc.approve(tenant, id).await().indefinitely();
        svc.goOnline(tenant, id).await().indefinitely();
        return id;
    }

    // ---------------------------------------------------------------- availability / index

    @Test
    void onboardingFlowAndEvents() {
        final var id = onlineDriver(VehicleType.CAR);
        assertThat(svc.get(tenant, id).await().indefinitely().status()).isEqualTo("ONLINE");
        assertThat(sink.types()).contains("mobility.driver.registered", "mobility.driver.approved", "mobility.driver.online");
        assertThat(svc.get(tenant, id).await().indefinitely().toString()).doesNotContain("+62 811");   // phone is never in the rider-facing view
    }

    @Test
    void unverifiedDriverCannotGoOnline() {
        final var id = UUID.randomUUID();
        svc.register(tenant, id, "D", "+62 811 9", new Vehicle(VehicleType.BIKE, "B 2 X", "", "")).await().indefinitely();
        assertThatThrownBy(() -> svc.goOnline(tenant, id).await().indefinitely()).isInstanceOf(DriverUnavailableException.class);
    }

    @Test
    void locationMakesTheDriverFindableAndGoingOfflineRemovesThem() {
        final var id = onlineDriver(VehicleType.CAR);
        assertThat(svc.updateLocation(tenant, id, MONAS, 0, 0, now.get()).await().indefinitely().outcome()).isEqualTo(LocationOutcome.STORED);
        assertThat(index.nearby(tenant, MONAS, 1000, VehicleType.CAR, now.get().minusSeconds(30), 5).await().indefinitely()).hasSize(1);
        svc.goOffline(tenant, id).await().indefinitely();
        assertThat(index.nearby(tenant, MONAS, 1000, VehicleType.CAR, now.get().minusSeconds(30), 5).await().indefinitely()).isEmpty();
    }

    @Test
    void claimedDriversDisappearFromSearchAndReturnOnRelease() {
        final var id = onlineDriver(VehicleType.CAR); final var ride = RideId.generate();
        svc.updateLocation(tenant, id, MONAS, 0, 0, now.get()).await().indefinitely();
        svc.claim(tenant, id, ride).await().indefinitely();
        assertThat(index.nearby(tenant, MONAS, 1000, VehicleType.CAR, now.get().minusSeconds(30), 5).await().indefinitely()).isEmpty();
        svc.release(tenant, id, RideId.generate()).await().indefinitely();     // wrong ride: nothing changes
        assertThat(index.nearby(tenant, MONAS, 1000, VehicleType.CAR, now.get().minusSeconds(30), 5).await().indefinitely()).isEmpty();
        svc.release(tenant, id, ride).await().indefinitely();
        assertThat(index.nearby(tenant, MONAS, 1000, VehicleType.CAR, now.get().minusSeconds(30), 5).await().indefinitely()).hasSize(1);
    }

    @Test
    void searchFiltersByVehicleRadiusFreshnessAndOrdersNearestFirst() {
        final var near = onlineDriver(VehicleType.CAR); final var far = onlineDriver(VehicleType.CAR);
        final var bike = onlineDriver(VehicleType.BIKE); final var stale = onlineDriver(VehicleType.CAR); final var outside = onlineDriver(VehicleType.CAR);
        svc.updateLocation(tenant, near, GeoMath.offset(MONAS, 300, 0), 0, 0, now.get()).await().indefinitely();
        svc.updateLocation(tenant, far, GeoMath.offset(MONAS, 0, 900), 0, 0, now.get()).await().indefinitely();
        svc.updateLocation(tenant, bike, GeoMath.offset(MONAS, 100, 0), 0, 0, now.get()).await().indefinitely();
        svc.updateLocation(tenant, outside, GeoMath.offset(MONAS, 4000, 0), 0, 0, now.get()).await().indefinitely();
        index.upsert(tenant, new DriverLocationIndex.Fix(DriverId.of(stale), VehicleType.CAR, GeoMath.offset(MONAS, 50, 0), 0, 0, now.get().minusSeconds(120), true)).await().indefinitely();

        final var found = index.nearby(tenant, MONAS, 1500, VehicleType.CAR, now.get().minusSeconds(30), 10).await().indefinitely();
        assertThat(found).extracting(n -> n.fix().driverId().value()).containsExactly(near, far);
        assertThat(found.get(0).distanceMeters()).isBetween(295.0, 305.0);
        assertThat(index.nearby(tenant, MONAS, 1500, VehicleType.CAR, now.get().minusSeconds(30), 1).await().indefinitely()).hasSize(1);
    }

    @Test
    void indexIsTenantIsolatedAndDriversChangeCellsWhenTheyMove() {
        final var id = onlineDriver(VehicleType.CAR);
        svc.updateLocation(tenant, id, MONAS, 0, 0, now.get()).await().indefinitely();
        assertThat(index.nearby(UUID.randomUUID(), MONAS, 1000, VehicleType.CAR, now.get().minusSeconds(30), 5).await().indefinitely()).isEmpty();
        now.set(now.get().plusSeconds(120));
        final var moved = GeoMath.offset(MONAS, 3000, 0);
        svc.updateLocation(tenant, id, moved, 0, 30, now.get()).await().indefinitely();
        assertThat(index.nearby(tenant, MONAS, 1000, VehicleType.CAR, now.get().minusSeconds(30), 5).await().indefinitely()).isEmpty();   // left the old cell
        assertThat(index.nearby(tenant, moved, 500, VehicleType.CAR, now.get().minusSeconds(30), 5).await().indefinitely()).hasSize(1);
    }

    // ---------------------------------------------------------------- location ingestion rules

    @Test
    void teleportsAreRejectedAndTinyMovesSkipped() {
        final var id = onlineDriver(VehicleType.CAR);
        svc.updateLocation(tenant, id, MONAS, 0, 0, now.get()).await().indefinitely();
        now.set(now.get().plusSeconds(1));
        assertThat(svc.updateLocation(tenant, id, GeoMath.offset(MONAS, 5, 5), 0, 0, now.get()).await().indefinitely().outcome())
                .isEqualTo(LocationOutcome.SKIPPED_UNCHANGED);                                          // 7 m in 1 s: nothing new
        now.set(now.get().plusSeconds(4));
        assertThat(svc.updateLocation(tenant, id, GeoMath.offset(MONAS, 30_000, 0), 0, 0, now.get()).await().indefinitely().outcome())
                .isEqualTo(LocationOutcome.REJECTED_IMPLAUSIBLE);                                       // 30 km in 5 s
        assertThat(svc.updateLocation(tenant, id, GeoMath.offset(MONAS, 80, 0), 0, 20, now.get()).await().indefinitely().outcome())
                .isEqualTo(LocationOutcome.STORED);                                                     // a rejected fix did not poison the next good one
    }

    @Test
    void staleOrFutureFixesAndOfflineDriversAreRejected() {
        final var id = onlineDriver(VehicleType.CAR);
        assertThat(svc.updateLocation(tenant, id, MONAS, 0, 0, now.get().minusSeconds(120)).await().indefinitely().outcome()).isEqualTo(LocationOutcome.REJECTED_STALE);
        assertThat(svc.updateLocation(tenant, id, MONAS, 0, 0, now.get().plusSeconds(120)).await().indefinitely().outcome()).isEqualTo(LocationOutcome.REJECTED_STALE);
        svc.goOffline(tenant, id).await().indefinitely();
        assertThat(svc.updateLocation(tenant, id, MONAS, 0, 0, now.get()).await().indefinitely().outcome()).isEqualTo(LocationOutcome.REJECTED_NOT_ONLINE);
    }

    @Test
    void locationUpdatesWhileOnTripReportTheActiveRideAndDoNotMakeTheDriverSearchable() {
        final var id = onlineDriver(VehicleType.CAR); final var ride = RideId.generate();
        svc.updateLocation(tenant, id, MONAS, 0, 0, now.get()).await().indefinitely();
        svc.claim(tenant, id, ride).await().indefinitely();
        now.set(now.get().plusSeconds(10));
        final var r = svc.updateLocation(tenant, id, GeoMath.offset(MONAS, 100, 0), 0, 36, now.get()).await().indefinitely();
        assertThat(r.outcome()).isEqualTo(LocationOutcome.STORED);
        assertThat(r.activeRideId()).isEqualTo(ride.value());
        assertThat(index.nearby(tenant, MONAS, 1000, VehicleType.CAR, now.get().minusSeconds(30), 5).await().indefinitely()).isEmpty();
    }

    // ---------------------------------------------------------------- the double-assignment race

    @Test
    void whenManyRidesRaceForOneDriverExactlyOneWins() throws Exception {
        for (int round = 0; round < 15; round++) {
            setUp();
            final var id = onlineDriver(VehicleType.CAR);
            final int rides = 8;
            final var pool = Executors.newFixedThreadPool(rides); final var go = new CountDownLatch(1);
            final List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < rides; i++) {
                results.add(pool.submit(() -> {
                    go.await();
                    try { svc.claim(tenant, id, RideId.generate()).await().indefinitely(); return true; }
                    catch (DriverUnavailableException e) { return false; }
                }));
            }
            go.countDown();
            int wins = 0;
            for (var f : results) if (f.get(10, TimeUnit.SECONDS)) wins++;
            pool.shutdown();
            assertThat(wins).as("round %d", round).isEqualTo(1);
            assertThat(svc.get(tenant, id).await().indefinitely().status()).isEqualTo("ON_TRIP");
        }
    }

    @Test
    void unknownDriverIsNotFoundAndOtherTenantsCannotSeeDrivers() {
        final var id = onlineDriver(VehicleType.CAR);
        assertThatThrownBy(() -> svc.get(UUID.randomUUID(), id).await().indefinitely()).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> svc.goOnline(tenant, UUID.randomUUID()).await().indefinitely()).isInstanceOf(NotFoundException.class);
    }

    @Test
    void cancellationsFeedSuspensionAndRemoveTheDriverFromSearch() {
        final var id = onlineDriver(VehicleType.CAR);
        svc.updateLocation(tenant, id, MONAS, 0, 0, now.get()).await().indefinitely();
        for (int i = 0; i < 3; i++) svc.recordCancellation(tenant, id).await().indefinitely();
        assertThat(sink.types()).contains("mobility.driver.suspended");
        assertThatThrownBy(() -> svc.claim(tenant, id, RideId.generate()).await().indefinitely()).isInstanceOf(DriverUnavailableException.class);
    }
}
