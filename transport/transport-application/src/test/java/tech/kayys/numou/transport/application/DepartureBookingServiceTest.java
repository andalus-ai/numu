package tech.kayys.numou.transport.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.transport.domain.DepartureInventory;
import tech.kayys.numou.transport.domain.PassengerHold;
import tech.kayys.numou.transport.domain.PassengerHoldStatus;
import tech.kayys.numou.transport.domain.RouteStop;
import tech.kayys.numou.transport.domain.RouteStatus;
import tech.kayys.numou.transport.domain.ScheduledDeparture;
import tech.kayys.numou.transport.domain.ScheduledStopTime;
import tech.kayys.numou.transport.domain.TransportRoute;
import tech.kayys.numou.transport.spi.DepartureInventoryRepository;
import tech.kayys.numou.transport.spi.InventoryConcurrencyException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DepartureBookingServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-27T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void idempotentReplayReturnsCanonicalHoldWithoutSecondAllocation() {
        Fixture fixture = new Fixture(2);
        DepartureBookingService service = fixture.service();
        var command = new DepartureBookingService.HoldCommand("tenant-a", fixture.departureId,
                "request-1", 1, 3, 2, NOW.plusSeconds(300));

        PassengerHold first = service.hold(command);
        PassengerHold replay = service.hold(command);

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(replay.status()).isEqualTo(PassengerHoldStatus.HELD);
        assertThat(fixture.repository.find("tenant-a", fixture.departureId).orElseThrow().holds()).hasSize(1);
        assertThat(fixture.repository.saves).isEqualTo(1);
    }

    @Test
    void idempotencyKeyCannotBeReusedForDifferentPassengerRequest() {
        Fixture fixture = new Fixture(3);
        DepartureBookingService service = fixture.service();
        service.hold(new DepartureBookingService.HoldCommand("tenant-a", fixture.departureId,
                "same-key", 1, 2, 1, NOW.plusSeconds(300)));

        assertThatThrownBy(() -> service.hold(new DepartureBookingService.HoldCommand("tenant-a",
                fixture.departureId, "same-key", 1, 3, 1, NOW.plusSeconds(300))))
                .isInstanceOf(DepartureInventory.IdempotencyConflictException.class);
    }

    @Test
    void optimisticConflictReloadsAndReevaluatesCommand() {
        Fixture fixture = new Fixture(2);
        fixture.repository.conflictsToInject = 1;

        PassengerHold hold = fixture.service().hold(new DepartureBookingService.HoldCommand(
                "tenant-a", fixture.departureId, "retry-key", 1, 3, 1, NOW.plusSeconds(300)));

        assertThat(hold.status()).isEqualTo(PassengerHoldStatus.HELD);
        assertThat(fixture.repository.find("tenant-a", fixture.departureId).orElseThrow().holds()).hasSize(1);
        assertThat(fixture.repository.saveAttempts).isEqualTo(2);
        assertThat(fixture.repository.saves).isEqualTo(1);
    }

    @Test
    void expiredHoldIsPersistedBeforeConfirmationFails() {
        Fixture fixture = new Fixture(1);
        DepartureBookingService service = fixture.service();
        PassengerHold hold = service.hold(new DepartureBookingService.HoldCommand("tenant-a",
                fixture.departureId, "expire-me", 1, 3, 1, NOW.plusSeconds(30)));
        fixture.clockAdvanceTo(NOW.plusSeconds(30));

        assertThatThrownBy(() -> service.confirm("tenant-a", fixture.departureId, hold.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only an active hold");
        assertThat(fixture.repository.find("tenant-a", fixture.departureId).orElseThrow()
                .holds().get(0).status()).isEqualTo(PassengerHoldStatus.EXPIRED);
    }

    @Test
    void boundedExpiryBatchPersistsTransitionsAndIsIdempotent() {
        Fixture fixture = new Fixture(2);
        DepartureBookingService service = fixture.service();
        service.hold(new DepartureBookingService.HoldCommand("tenant-a", fixture.departureId,
                "due-one", 1, 3, 1, NOW.plusSeconds(5)));
        fixture.clockAdvanceTo(NOW.plusSeconds(5));

        assertThat(service.expireDueHolds(10)).isEqualTo(1);
        assertThat(service.expireDueHolds(10)).isZero();
        assertThat(fixture.repository.find("tenant-a", fixture.departureId).orElseThrow()
                .holds().get(0).status()).isEqualTo(PassengerHoldStatus.EXPIRED);
    }

    @Test
    void expiryBatchRejectsUnboundedOrInvalidLimits() {
        Fixture fixture = new Fixture(2);
        assertThatThrownBy(() -> fixture.service().expireDueHolds(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fixture.service().expireDueHolds(10_001))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class Fixture {
        private final UUID departureId = UUID.randomUUID();
        private final InMemoryRepository repository = new InMemoryRepository();
        private Clock clock = CLOCK;

        private Fixture(int capacity) {
            TransportRoute route = new TransportRoute(UUID.randomUUID(), "tenant-a", "R-1", "Test route",
                    List.of(new RouteStop(UUID.randomUUID(), 1, true, false),
                            new RouteStop(UUID.randomUUID(), 2, true, true),
                            new RouteStop(UUID.randomUUID(), 3, false, true)), RouteStatus.PUBLISHED);
            ScheduledDeparture departure = ScheduledDeparture.draft(departureId, route, "svc-1",
                    List.of(new ScheduledStopTime(route.stops().get(0).stopId(), 1, null, NOW.plusSeconds(600)),
                            new ScheduledStopTime(route.stops().get(1).stopId(), 2, NOW.plusSeconds(900), NOW.plusSeconds(960)),
                            new ScheduledStopTime(route.stops().get(2).stopId(), 3, NOW.plusSeconds(1200), null)),
                    capacity).publish();
            repository.create(DepartureInventory.open(departure));
        }

        private DepartureBookingService service() {
            return new DepartureBookingService(repository, clock);
        }

        private void clockAdvanceTo(Instant instant) {
            clock = Clock.fixed(instant, ZoneOffset.UTC);
        }
    }

    private static final class InMemoryRepository implements DepartureInventoryRepository {
        private final Map<String, DepartureInventory> snapshots = new HashMap<>();
        private int conflictsToInject;
        private int saveAttempts;
        private int saves;

        @Override
        public Optional<DepartureInventory> find(String tenantId, UUID departureId) {
            return Optional.ofNullable(snapshots.get(key(tenantId, departureId)));
        }

        @Override
        public List<InventoryKey> findDueInventories(Instant now, int limit) {
            List<InventoryKey> due = new ArrayList<>();
            snapshots.values().stream()
                    .filter(inventory -> inventory.holds().stream().anyMatch(hold ->
                            hold.status() == PassengerHoldStatus.HELD && !hold.expiresAt().isAfter(now)))
                    .sorted(java.util.Comparator.comparing(DepartureInventory::tenantId)
                            .thenComparing(DepartureInventory::departureId))
                    .limit(limit)
                    .forEach(inventory -> due.add(new InventoryKey(inventory.tenantId(), inventory.departureId())));
            return List.copyOf(due);
        }

        @Override
        public void create(DepartureInventory inventory) {
            if (snapshots.putIfAbsent(key(inventory.tenantId(), inventory.departureId()), inventory) != null) {
                throw new InventoryConcurrencyException("already exists");
            }
        }

        @Override
        public void save(DepartureInventory inventory, int expectedVersion) {
            saveAttempts++;
            if (conflictsToInject > 0) {
                conflictsToInject--;
                throw new InventoryConcurrencyException("simulated stale writer");
            }
            String key = key(inventory.tenantId(), inventory.departureId());
            DepartureInventory stored = snapshots.get(key);
            if (stored == null || stored.version() != expectedVersion) {
                throw new InventoryConcurrencyException("stale inventory");
            }
            snapshots.put(key, inventory);
            saves++;
        }

        private static String key(String tenantId, UUID departureId) {
            return tenantId + "/" + departureId;
        }
    }
}
