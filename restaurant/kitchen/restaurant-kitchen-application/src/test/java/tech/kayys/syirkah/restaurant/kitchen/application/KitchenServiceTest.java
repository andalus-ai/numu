package tech.kayys.syirkah.restaurant.kitchen.application;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.application.transaction.UnitOfWork;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.restaurant.kernel.domain.*;
import tech.kayys.syirkah.aggregate.domain.*;
import tech.kayys.syirkah.aggregate.memory.RecordingEventSink;
import tech.kayys.syirkah.restaurant.kitchen.adapter.memory.*;
import tech.kayys.syirkah.restaurant.kitchen.application.KitchenService.*;
import tech.kayys.syirkah.restaurant.kitchen.domain.Priority;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KitchenServiceTest {

    final UUID tenant = UUID.randomUUID();
    final UUID venue = UUID.randomUUID();
    final UUID grill = UUID.randomUUID();
    final UUID bar = UUID.randomUUID();
    final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-07T12:00:00Z"));
    final UnitOfWork uow = new UnitOfWork() { public <R> Uni<R> execute(java.util.function.Supplier<Uni<R>> w) { return w.get(); } };
    final RecordingEventSink sink = new RecordingEventSink();
    KitchenService svc;

    @BeforeEach
    void setUp() {
        svc = new KitchenService(new InMemoryTicketRepository(), new InMemoryAvailabilityBoardRepository(),
                (t, v, s) -> Uni.createFrom().item(2), uow, sink, now::get);
    }

    FiredLine line(String name, int prepMin, UUID station, String... allergens) {
        return new FiredLine(UUID.randomUUID(), UUID.randomUUID(), name, 1, List.of(), "", Set.of(allergens), prepMin * 60L, station);
    }

    FireCommand fire(UUID orderId, int course, Priority p, FiredLine... lines) {
        return new FireCommand(tenant, venue, orderId, course, "T5", p, List.of(lines));
    }

    @Test
    void firingACourseCreatesOneTicketPerStation() {
        final var tickets = svc.fire(fire(UUID.randomUUID(), 1, Priority.NORMAL, line("steak", 12, grill), line("fries", 6, grill), line("mojito", 3, bar)))
                .await().indefinitely();
        assertThat(tickets).hasSize(2);
        assertThat(tickets).extracting(TicketView::stationId).containsExactlyInAnyOrder(grill, bar);
        assertThat(sink.types()).containsOnly("restaurant.kitchen.ticket.created");
        assertThat(sink.all()).hasSize(2);
    }

    @Test
    void firingTheSameCourseTwiceDoesNotDuplicateTickets() {
        final var order = UUID.randomUUID();
        final var cmd = fire(order, 1, Priority.NORMAL, line("steak", 12, grill));
        final var first = svc.fire(cmd).await().indefinitely();
        final var again = svc.fire(cmd).await().indefinitely(); // e.g. redelivered "course fired" event
        assertThat(again).extracting(TicketView::id).isEqualTo(first.stream().map(TicketView::id).toList());
        assertThat(svc.queue(tenant, venue, Optional.empty()).await().indefinitely()).hasSize(1);
        assertThat(sink.all()).hasSize(1);
    }

    @Test
    void concurrentDuplicateFiresStillYieldOneTicket() throws Exception {
        for (int round = 0; round < 10; round++) {
            setUp();
            final var order = UUID.randomUUID();
            final var cmd = fire(order, 1, Priority.NORMAL, line("steak", 12, grill));
            final var pool = Executors.newFixedThreadPool(6); final var go = new CountDownLatch(1);
            final List<Future<?>> fs = new ArrayList<>();
            for (int i = 0; i < 6; i++) fs.add(pool.submit(() -> { go.await(); svc.fire(cmd).await().indefinitely(); return null; }));
            go.countDown();
            for (var f : fs) f.get(10, TimeUnit.SECONDS);
            pool.shutdown();
            assertThat(svc.queue(tenant, venue, Optional.empty()).await().indefinitely()).hasSize(1);
        }
    }

    @Test
    void differentCoursesOfTheSameOrderAreSeparateTickets() {
        final var order = UUID.randomUUID();
        svc.fire(fire(order, 1, Priority.NORMAL, line("soup", 5, grill))).await().indefinitely();
        svc.fire(fire(order, 2, Priority.NORMAL, line("steak", 12, grill))).await().indefinitely();
        assertThat(svc.queue(tenant, venue, Optional.empty()).await().indefinitely()).hasSize(2);
    }

    @Test
    void kdsQueueOrdersByPriorityThenByUrgency() {
        svc.fire(fire(UUID.randomUUID(), 1, Priority.NORMAL, line("slow", 20, grill))).await().indefinitely();
        svc.fire(fire(UUID.randomUUID(), 1, Priority.NORMAL, line("quick", 3, grill))).await().indefinitely();
        final var vip = svc.fire(fire(UUID.randomUUID(), 1, Priority.VIP, line("vip dish", 30, grill))).await().indefinitely().get(0);
        final var q = svc.queue(tenant, venue, Optional.empty()).await().indefinitely();
        assertThat(q.get(0).id()).isEqualTo(vip.id());                       // VIP first even though it is the slowest
        assertThat(q.get(1).items().get(0).name()).isEqualTo("quick");       // then earliest promise
        assertThat(q.get(2).items().get(0).name()).isEqualTo("slow");
    }

    @Test
    void busyStationQuotesALaterPromiseForNewTickets() {
        // parallelism is 2 per the fake directory; load the grill with 4 items first
        svc.fire(fire(UUID.randomUUID(), 1, Priority.NORMAL, line("a", 10, grill), line("b", 10, grill), line("c", 10, grill), line("d", 10, grill)))
                .await().indefinitely();
        final var next = svc.fire(fire(UUID.randomUUID(), 1, Priority.NORMAL, line("e", 10, grill))).await().indefinitely().get(0);
        assertThat(next.promisedAt()).isEqualTo(now.get().plus(Duration.ofMinutes(10 + 10))); // 2 waves x 10m / 2
    }

    @Test
    void cookingFlowAndLateDetection() {
        final var l = line("steak", 12, grill);
        final var t = svc.fire(fire(UUID.randomUUID(), 1, Priority.NORMAL, l)).await().indefinitely().get(0);
        svc.start(tenant, t.id(), l.lineId()).await().indefinitely();
        assertThat(svc.lateCount(tenant, venue).await().indefinitely()).isZero();
        now.set(now.get().plus(Duration.ofMinutes(13)));
        assertThat(svc.lateCount(tenant, venue).await().indefinitely()).isEqualTo(1);
        assertThat(svc.queue(tenant, venue, Optional.empty()).await().indefinitely().get(0).late()).isTrue();
        svc.markReady(tenant, t.id(), l.lineId()).await().indefinitely();
        assertThat(svc.lateCount(tenant, venue).await().indefinitely()).isZero();
        assertThat(svc.bump(tenant, t.id()).await().indefinitely().status()).isEqualTo("BUMPED");
        assertThat(svc.queue(tenant, venue, Optional.empty()).await().indefinitely()).isEmpty(); // off the screen
        assertThat(svc.recall(tenant, t.id()).await().indefinitely().status()).isEqualTo("READY");
        assertThat(sink.types()).contains("restaurant.kitchen.ticket.bumped", "restaurant.kitchen.ticket.recalled", "restaurant.kitchen.ticket.ready");
    }

    @Test
    void cannotBumpUnfinishedTicketAndNothingIsPublishedForTheFailure() {
        final var t = svc.fire(fire(UUID.randomUUID(), 1, Priority.NORMAL, line("steak", 12, grill))).await().indefinitely().get(0);
        final int before = sink.all().size();
        assertThatThrownBy(() -> svc.bump(tenant, t.id()).await().indefinitely()).isInstanceOf(BusinessRuleViolation.class);
        assertThat(sink.all()).hasSize(before);
    }

    @Test
    void orderVoidReachesTheRightTicketAndIsIdempotent() {
        final var order = UUID.randomUUID(); final var a = line("steak", 12, grill); final var b = line("mojito", 3, bar);
        svc.fire(fire(order, 1, Priority.NORMAL, a, b)).await().indefinitely();
        svc.voidLineOfOrder(tenant, order, a.lineId(), "guest changed mind").await().indefinitely();
        svc.voidLineOfOrder(tenant, order, a.lineId(), "guest changed mind").await().indefinitely();
        final var q = svc.queue(tenant, venue, Optional.empty()).await().indefinitely();
        assertThat(q).hasSize(1);
        assertThat(q.get(0).stationId()).isEqualTo(bar);
        assertThat(sink.types().stream().filter(x -> x.equals("restaurant.kitchen.item.voided"))).hasSize(1);
    }

    @Test
    void stationFilterShowsOnlyThatStation() {
        svc.fire(fire(UUID.randomUUID(), 1, Priority.NORMAL, line("steak", 12, grill), line("mojito", 3, bar))).await().indefinitely();
        assertThat(svc.queue(tenant, venue, Optional.of(bar)).await().indefinitely()).hasSize(1);
    }

    @Test
    void tenantsNeverSeeEachOthersTicketsOrActOnThem() {
        final var t = svc.fire(fire(UUID.randomUUID(), 1, Priority.NORMAL, line("steak", 12, grill))).await().indefinitely().get(0);
        final var other = UUID.randomUUID();
        assertThat(svc.queue(other, venue, Optional.empty()).await().indefinitely()).isEmpty();
        assertThatThrownBy(() -> svc.bump(other, t.id()).await().indefinitely()).isInstanceOf(NotFoundException.class);
    }

    @Test
    void eightySixBoardBlocksAndRestoresDishesPerTenant() {
        final var dish = UUID.randomUUID();
        assertThat(svc.isAvailable(tenant, venue, dish).await().indefinitely()).isTrue();
        svc.eightySix(tenant, venue, dish, "sold out", null).await().indefinitely();
        assertThat(svc.isAvailable(tenant, venue, dish).await().indefinitely()).isFalse();
        assertThat(svc.isAvailable(UUID.randomUUID(), venue, dish).await().indefinitely()).isTrue(); // other tenant unaffected
        assertThat(svc.unavailable(tenant, venue).await().indefinitely()).hasSize(1);
        svc.restore(tenant, venue, dish).await().indefinitely();
        assertThat(svc.isAvailable(tenant, venue, dish).await().indefinitely()).isTrue();
        svc.restore(tenant, venue, dish).await().indefinitely(); // restoring something not 86'd is a quiet no-op
        assertThat(sink.types()).containsExactly("restaurant.kitchen.item.eighty-sixed", "restaurant.kitchen.item.restored");
    }
}
