package tech.kayys.numou.transport.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DepartureInventoryTest {
    private static final Instant NOW = Instant.parse("2026-10-26T08:00:00Z");
    private static final Instant EXPIRY = NOW.plusSeconds(300);

    private DepartureInventory inventory(int capacity) {
        TransportRoute route = new TransportRoute(UUID.randomUUID(), "tenant-a", "R-1", "City route",
                List.of(
                        new RouteStop(UUID.randomUUID(), 1, true, false),
                        new RouteStop(UUID.randomUUID(), 2, true, true),
                        new RouteStop(UUID.randomUUID(), 3, false, true)),
                RouteStatus.PUBLISHED);
        List<ScheduledStopTime> times = List.of(
                new ScheduledStopTime(route.stops().get(0).stopId(), 1, null, NOW.plusSeconds(600)),
                new ScheduledStopTime(route.stops().get(1).stopId(), 2, NOW.plusSeconds(1200), NOW.plusSeconds(1260)),
                new ScheduledStopTime(route.stops().get(2).stopId(), 3, NOW.plusSeconds(1800), null));
        ScheduledDeparture departure = ScheduledDeparture.draft(UUID.randomUUID(), route, "svc-1", times, capacity)
                .publish();
        return DepartureInventory.open(departure);
    }

    @Test
    void holdsCapacityOnlyOnJourneySegments() {
        DepartureInventory start = inventory(3);
        DepartureInventory.HoldResult first = start.hold(UUID.randomUUID(), "req-1", "fingerprint-1",
                1, 2, 2, EXPIRY, NOW);
        DepartureInventory afterSecond = first.inventory().hold(UUID.randomUUID(), "req-2", "fingerprint-2",
                2, 3, 2, EXPIRY, NOW).inventory();

        assertThat(afterSecond.remainingCapacity(1, 2, NOW)).isEqualTo(1);
        assertThat(afterSecond.remainingCapacity(2, 3, NOW)).isEqualTo(1);
        assertThat(afterSecond.remainingCapacity(1, 3, NOW)).isEqualTo(0);
    }

    @Test
    void rejectsOversellOnOverlappingSegment() {
        DepartureInventory start = inventory(2);
        DepartureInventory.HoldResult first = start.hold(UUID.randomUUID(), "req-1", "fp-1",
                1, 3, 2, EXPIRY, NOW);

        assertThatThrownBy(() -> first.inventory().hold(UUID.randomUUID(), "req-2", "fp-2",
                2, 3, 1, EXPIRY, NOW))
                .isInstanceOf(DepartureInventory.CapacityUnavailableException.class);
    }

    @Test
    void sameIdempotencyKeyAndFingerprintDoesNotAllocateTwice() {
        DepartureInventory start = inventory(2);
        UUID holdId = UUID.randomUUID();
        DepartureInventory.HoldResult first = start.hold(holdId, "same-key", "same-fingerprint",
                1, 3, 2, EXPIRY, NOW);
        DepartureInventory.HoldResult replay = first.inventory().hold(UUID.randomUUID(), "same-key",
                "same-fingerprint", 1, 3, 2, EXPIRY, NOW);

        assertThat(replay.created()).isFalse();
        assertThat(replay.hold().id()).isEqualTo(holdId);
        assertThat(replay.inventory().holds()).hasSize(1);
        assertThat(replay.inventory().remainingCapacity(1, 3, NOW)).isZero();
    }

    @Test
    void rejectsIdempotencyKeyReuseWithDifferentRequest() {
        DepartureInventory start = inventory(2);
        DepartureInventory.HoldResult first = start.hold(UUID.randomUUID(), "same-key", "fp-1",
                1, 2, 1, EXPIRY, NOW);

        assertThatThrownBy(() -> first.inventory().hold(UUID.randomUUID(), "same-key", "fp-2",
                1, 3, 1, EXPIRY, NOW))
                .isInstanceOf(DepartureInventory.IdempotencyConflictException.class);
    }

    @Test
    void expiredHoldStopsConsumingCapacityAndRemainsAuditable() {
        DepartureInventory start = inventory(1);
        DepartureInventory.HoldResult first = start.hold(UUID.randomUUID(), "req-1", "fp-1",
                1, 3, 1, EXPIRY, NOW);
        Instant afterExpiry = EXPIRY.plusSeconds(1);

        DepartureInventory expired = first.inventory().expireDue(afterExpiry);
        assertThat(expired.remainingCapacity(1, 3, afterExpiry)).isEqualTo(1);
        assertThat(expired.holds()).singleElement().extracting(PassengerHold::status)
                .isEqualTo(PassengerHoldStatus.EXPIRED);
    }

    @Test
    void confirmedHoldDoesNotExpireAndReleasePreservesHistory() {
        DepartureInventory start = inventory(1);
        DepartureInventory.HoldResult first = start.hold(UUID.randomUUID(), "req-1", "fp-1",
                1, 3, 1, EXPIRY, NOW);
        DepartureInventory confirmed = first.inventory().confirm(first.hold().id(), NOW.plusSeconds(1));
        DepartureInventory afterExpiry = confirmed.expireDue(EXPIRY.plusSeconds(10));

        assertThat(afterExpiry.holds()).singleElement().extracting(PassengerHold::status)
                .isEqualTo(PassengerHoldStatus.CONFIRMED);
        assertThat(afterExpiry.remainingCapacity(1, 3, EXPIRY.plusSeconds(10))).isZero();

        DepartureInventory released = afterExpiry.release(first.hold().id(), EXPIRY.plusSeconds(11));
        assertThat(released.remainingCapacity(1, 3, EXPIRY.plusSeconds(11))).isEqualTo(1);
        assertThat(released.holds()).hasSize(1);
        assertThat(released.holds().get(0).status()).isEqualTo(PassengerHoldStatus.RELEASED);
    }

    @Test
    void onlyPublishedDeparturesCanHaveInventory() {
        TransportRoute route = new TransportRoute(UUID.randomUUID(), "tenant-a", "R-2", "Route",
                List.of(
                        new RouteStop(UUID.randomUUID(), 1, true, false),
                        new RouteStop(UUID.randomUUID(), 2, false, true)),
                RouteStatus.PUBLISHED);
        ScheduledDeparture departure = ScheduledDeparture.draft(UUID.randomUUID(), route, "svc-2",
                List.of(
                        new ScheduledStopTime(route.stops().get(0).stopId(), 1, null, NOW.plusSeconds(600)),
                        new ScheduledStopTime(route.stops().get(1).stopId(), 2, NOW.plusSeconds(1200), null)),
                4);

        assertThatThrownBy(() -> DepartureInventory.open(departure))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
