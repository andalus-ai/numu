package tech.kayys.numou.restaurant.kitchen.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.restaurant.kernel.domain.*;
import tech.kayys.numou.restaurant.kitchen.domain.TicketRouter.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KitchenDomainTest {

    static final Instant T0 = Instant.parse("2026-10-07T12:00:00Z");
    static final VenueId VENUE = VenueId.generate();
    static final StationId GRILL = StationId.generate();
    static final StationId BAR = StationId.generate();

    static TicketItem item(String name, int prepMin, String... allergens) {
        return new TicketItem(UUID.randomUUID(), UUID.randomUUID(), name, 1, List.of(), "", Set.of(allergens), Duration.ofMinutes(prepMin), ItemState.QUEUED);
    }

    static KitchenTicket ticket(TicketItem... items) {
        return KitchenTicket.create(TicketId.generate(), OrderId.generate(), VENUE, GRILL, 1, "T5", Priority.NORMAL,
                List.of(items), T0, T0.plus(Duration.ofMinutes(15)));
    }

    @Test
    void statusIsDerivedFromItems() {
        final var a = item("steak", 12); final var b = item("fries", 6);
        final var t = ticket(a, b);
        assertThat(t.status()).isEqualTo(TicketStatus.NEW);
        t.start(a.lineId(), T0);
        assertThat(t.status()).isEqualTo(TicketStatus.IN_PROGRESS);
        t.markReady(a.lineId(), T0);
        assertThat(t.status()).isEqualTo(TicketStatus.IN_PROGRESS);
        t.markReady(b.lineId(), T0); // may be marked ready straight from QUEUED (cold items)
        assertThat(t.status()).isEqualTo(TicketStatus.READY);
    }

    @Test
    void ticketReadyEventFiresExactlyWhenTheLastItemIsReady() {
        final var a = item("steak", 12); final var b = item("fries", 6);
        final var t = ticket(a, b); t.pullDomainEvents();
        t.markReady(a.lineId(), T0);
        assertThat(t.pullDomainEvents()).extracting(e -> e.eventType()).containsExactly("restaurant.kitchen.item.ready");
        t.markReady(b.lineId(), T0);
        assertThat(t.pullDomainEvents()).extracting(e -> e.eventType()).containsExactly("restaurant.kitchen.item.ready", "restaurant.kitchen.ticket.ready");
    }

    @Test
    void cannotBumpWhileAnythingIsStillCooking() {
        final var a = item("steak", 12); final var b = item("fries", 6);
        final var t = ticket(a, b);
        t.markReady(a.lineId(), T0);
        assertThatThrownBy(() -> t.bump(T0)).isInstanceOf(BusinessRuleViolation.class);
        t.markReady(b.lineId(), T0);
        t.bump(T0.plus(Duration.ofMinutes(13)));
        assertThat(t.status()).isEqualTo(TicketStatus.BUMPED);
        assertThat(t.pullDomainEvents()).anySatisfy(e -> assertThat(e.toString()).contains("cookSeconds=780"));
    }

    @Test
    void recallBringsABumpedTicketBack() {
        final var a = item("steak", 12); final var t = ticket(a);
        t.markReady(a.lineId(), T0); t.bump(T0);
        t.recall(T0);
        assertThat(t.status()).isEqualTo(TicketStatus.READY);
        assertThatThrownBy(() -> t.recall(T0)).isInstanceOf(InvalidStateException.class);
    }

    @Test
    void voidingTheLastLiveItemCancelsTheTicketAndVoidIsIdempotent() {
        final var a = item("steak", 12); final var b = item("fries", 6);
        final var t = ticket(a, b); t.pullDomainEvents();
        t.voidItem(a.lineId(), "guest changed mind", T0);
        t.voidItem(a.lineId(), "guest changed mind", T0); // replayed void: no second event
        assertThat(t.status()).isEqualTo(TicketStatus.NEW);
        t.voidItem(b.lineId(), "86", T0);
        assertThat(t.status()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(t.pullDomainEvents()).extracting(e -> e.eventType())
                .containsExactly("restaurant.kitchen.item.voided", "restaurant.kitchen.item.voided", "restaurant.kitchen.ticket.cancelled");
        assertThatThrownBy(() -> t.start(a.lineId(), T0)).isInstanceOf(InvalidStateException.class);
    }

    @Test
    void voidingTheOnlyUnfinishedItemCompletesTheTicket() {
        final var a = item("steak", 12); final var b = item("fries", 6);
        final var t = ticket(a, b); t.markReady(a.lineId(), T0); t.pullDomainEvents();
        t.voidItem(b.lineId(), "out of potatoes", T0);
        assertThat(t.status()).isEqualTo(TicketStatus.READY);
        assertThat(t.pullDomainEvents()).extracting(e -> e.eventType()).contains("restaurant.kitchen.ticket.ready");
    }

    @Test
    void lateOnlyWhileStillCookingPastThePromise() {
        final var a = item("steak", 12); final var t = ticket(a);
        assertThat(t.isLate(T0.plus(Duration.ofMinutes(15)))).isFalse();
        assertThat(t.isLate(T0.plus(Duration.ofMinutes(16)))).isTrue();
        t.markReady(a.lineId(), T0.plus(Duration.ofMinutes(16)));
        assertThat(t.isLate(T0.plus(Duration.ofMinutes(30)))).isFalse(); // ready food waiting for a runner is not "late cooking"
    }

    @Test
    void invalidTransitionsAreRejected() {
        final var a = item("steak", 12); final var t = ticket(a);
        t.start(a.lineId(), T0);
        assertThatThrownBy(() -> t.start(a.lineId(), T0)).isInstanceOf(InvalidStateException.class);
        assertThatThrownBy(() -> t.start(UUID.randomUUID(), T0)).isInstanceOf(InvalidStateException.class);
    }

    @Test
    void allergensAreFlagged() {
        assertThat(ticket(item("pad thai", 8, "peanut")).hasAllergens()).isTrue();
        assertThat(ticket(item("salad", 3)).hasAllergens()).isFalse();
    }

    @Test
    void priorityChangeEmitsOnlyOnRealChange() {
        final var t = ticket(item("steak", 12)); t.pullDomainEvents();
        t.changePriority(Priority.NORMAL, T0);
        assertThat(t.pullDomainEvents()).isEmpty();
        t.changePriority(Priority.RUSH, T0);
        assertThat(t.priority()).isEqualTo(Priority.RUSH);
        assertThat(t.pullDomainEvents()).hasSize(1);
    }

    @Test
    void ticketSnapshotRoundTrip() {
        final var a = item("steak", 12, "gluten"); final var b = item("fries", 6);
        final var t = ticket(a, b); t.start(a.lineId(), T0); t.markReady(b.lineId(), T0);
        final var back = KitchenTicket.restore(t.snapshot());
        assertThat(back.snapshot()).isEqualTo(t.snapshot());
        assertThat(back.status()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(back.pullDomainEvents()).isEmpty();
    }

    // ---- router ----

    @Test
    void routerSplitsByStationAndUsesLongestPrep() {
        final var steak = item("steak", 12); final var fries = item("fries", 6); final var mojito = item("mojito", 3);
        final var drafts = TicketRouter.route(List.of(new RoutedLine(steak, GRILL), new RoutedLine(fries, GRILL), new RoutedLine(mojito, BAR)),
                T0, s -> new StationLoad(0, 2));
        assertThat(drafts).hasSize(2);
        final var grill = drafts.stream().filter(d -> d.station().equals(GRILL)).findFirst().orElseThrow();
        final var bar = drafts.stream().filter(d -> d.station().equals(BAR)).findFirst().orElseThrow();
        assertThat(grill.items()).hasSize(2);
        assertThat(grill.promisedAt()).isEqualTo(T0.plus(Duration.ofMinutes(12)));
        assertThat(bar.promisedAt()).isEqualTo(T0.plus(Duration.ofMinutes(3)));
    }

    @Test
    void busyStationsPromiseLater() {
        final var steak = item("steak", 12);
        final var idle = TicketRouter.route(List.of(new RoutedLine(steak, GRILL)), T0, s -> new StationLoad(0, 2)).get(0).promisedAt();
        final var light = TicketRouter.route(List.of(new RoutedLine(steak, GRILL)), T0, s -> new StationLoad(1, 2)).get(0).promisedAt();
        final var busy = TicketRouter.route(List.of(new RoutedLine(steak, GRILL)), T0, s -> new StationLoad(8, 2)).get(0).promisedAt();
        assertThat(light).isEqualTo(idle);                                        // under one full wave: no extra wait
        assertThat(busy).isEqualTo(T0.plus(Duration.ofMinutes(12 + 24)));         // 4 waves x 12m / 2
    }

    @Test
    void routingNothingIsAnError() {
        assertThatThrownBy(() -> TicketRouter.route(List.of(), T0, s -> new StationLoad(0, 1))).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- 86 board ----

    @Test
    void eightySixAndRestore() {
        final var board = AvailabilityBoard.openFor(VENUE, T0); final var dish = UUID.randomUUID();
        assertThat(board.isAvailable(dish, T0)).isTrue();
        board.eightySix(dish, "sold out", null, T0);
        assertThat(board.isAvailable(dish, T0.plusSeconds(1))).isFalse();
        assertThat(board.restore(dish, T0)).isTrue();
        assertThat(board.restore(dish, T0)).isFalse();
        assertThat(board.isAvailable(dish, T0)).isTrue();
    }

    @Test
    void timedEightySixLiftsItself() {
        final var board = AvailabilityBoard.openFor(VENUE, T0); final var dish = UUID.randomUUID();
        board.eightySix(dish, "waiting for delivery", T0.plus(Duration.ofHours(2)), T0);
        assertThat(board.isAvailable(dish, T0.plus(Duration.ofHours(1)))).isFalse();
        assertThat(board.isAvailable(dish, T0.plus(Duration.ofHours(2)))).isTrue();
        assertThat(board.unavailable(T0.plus(Duration.ofHours(3)))).isEmpty();
        assertThatThrownBy(() -> board.eightySix(dish, "x", T0.minusSeconds(1), T0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void boardSnapshotRoundTrip() {
        final var board = AvailabilityBoard.openFor(VENUE, T0); board.eightySix(UUID.randomUUID(), "x", null, T0);
        assertThat(AvailabilityBoard.restore(board.snapshot()).snapshot()).isEqualTo(board.snapshot());
    }
}
