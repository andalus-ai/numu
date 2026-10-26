package tech.kayys.numou.transport.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransportDomainTest {

    private static final String TENANT = "operator-id-1";
    private static final UUID STOP_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID STOP_B = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID STOP_C = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void routeNormalizesCodeAndCanonicalizesStopOrder() {
        TransportRoute route = new TransportRoute(
                UUID.randomUUID(), TENANT, " bdg-jkt ", "Bandung to Jakarta",
                List.of(new RouteStop(STOP_B, 2, true, true), new RouteStop(STOP_A, 1, true, false)),
                RouteStatus.DRAFT);

        assertThat(route.code()).isEqualTo("BDG-JKT");
        assertThat(route.stops()).extracting(RouteStop::stopId).containsExactly(STOP_A, STOP_B);
        assertThatThrownBy(() -> route.stops().add(new RouteStop(STOP_C, 3, true, true)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void routeRequiresAtLeastTwoStopsAndContiguousSequence() {
        assertThatThrownBy(() -> new TransportRoute(
                UUID.randomUUID(), TENANT, "R1", "Single stop",
                List.of(new RouteStop(STOP_A, 1, true, true)), RouteStatus.DRAFT))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new TransportRoute(
                UUID.randomUUID(), TENANT, "R1", "Gap in sequence",
                List.of(new RouteStop(STOP_A, 1, true, true), new RouteStop(STOP_B, 3, true, true)),
                RouteStatus.DRAFT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void routeLifecycleDoesNotMutateOriginal() {
        TransportRoute draft = routeDraft();
        TransportRoute published = draft.publish();

        assertThat(draft.status()).isEqualTo(RouteStatus.DRAFT);
        assertThat(published.status()).isEqualTo(RouteStatus.PUBLISHED);
        assertThat(published.retire().status()).isEqualTo(RouteStatus.RETIRED);
        assertThatThrownBy(published::publish).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void departureRequiresPublishedRouteAndMatchingOrderedStops() {
        List<ScheduledStopTime> times = validStopTimes();
        assertThatThrownBy(() -> ScheduledDeparture.draft(UUID.randomUUID(), routeDraft(), "S1", times, 40))
                .isInstanceOf(IllegalArgumentException.class);

        TransportRoute route = routeDraft().publish();
        List<ScheduledStopTime> wrongOrder = List.of(
                new ScheduledStopTime(STOP_B, 1, null, Instant.parse("2026-11-01T01:00:00Z")),
                new ScheduledStopTime(STOP_A, 2, Instant.parse("2026-11-01T02:00:00Z"), null));
        assertThatThrownBy(() -> ScheduledDeparture.draft(UUID.randomUUID(), route, "S1", wrongOrder, 40))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void departureEnforcesCapacityChronologyAndLifecycle() {
        ScheduledDeparture draft = ScheduledDeparture.draft(
                UUID.randomUUID(), routeDraft().publish(), " bus-101 ", validStopTimes(), 40);
        ScheduledDeparture published = draft.publish();

        assertThat(published.serviceCode()).isEqualTo("BUS-101");
        assertThat(published.tenantId()).isEqualTo(TENANT);
        assertThat(published.status()).isEqualTo(ScheduledDepartureStatus.PUBLISHED);
        assertThat(published.cancel().status()).isEqualTo(ScheduledDepartureStatus.CANCELLED);
        assertThatThrownBy(draft::cancel).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ScheduledDeparture.draft(
                UUID.randomUUID(), routeDraft().publish(), "S1", validStopTimes(), 0))
                .isInstanceOf(IllegalArgumentException.class);

        List<ScheduledStopTime> outOfOrder = List.of(
                new ScheduledStopTime(STOP_A, 1, null, Instant.parse("2026-11-01T03:00:00Z")),
                new ScheduledStopTime(STOP_B, 2, Instant.parse("2026-11-01T02:00:00Z"), null));
        assertThatThrownBy(() -> ScheduledDeparture.draft(
                UUID.randomUUID(), routeDraft().publish(), "S1", outOfOrder, 40))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static TransportRoute routeDraft() {
        return new TransportRoute(UUID.randomUUID(), TENANT, "BDG-JKT", "Bandung to Jakarta",
                List.of(new RouteStop(STOP_A, 1, true, false), new RouteStop(STOP_B, 2, true, true)),
                RouteStatus.DRAFT);
    }

    private static List<ScheduledStopTime> validStopTimes() {
        return List.of(
                new ScheduledStopTime(STOP_A, 1, null, Instant.parse("2026-11-01T01:00:00Z")),
                new ScheduledStopTime(STOP_B, 2, Instant.parse("2026-11-01T03:00:00Z"), null));
    }
}
