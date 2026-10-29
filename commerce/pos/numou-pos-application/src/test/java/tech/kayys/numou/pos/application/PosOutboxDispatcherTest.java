package tech.kayys.numou.pos.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.pos.application.port.out.PosEventPublisher;
import tech.kayys.numou.pos.application.port.out.PosOutboxDelivery;
import tech.kayys.numou.pos.application.port.out.PosOutboxDeliveryStore;
import tech.kayys.numou.pos.application.port.out.PosOutboxEvent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PosOutboxDispatcherTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void publishesClaimedEventsAndMarksThemPublished() {
        FakeStore store = new FakeStore();
        store.deliveries.add(delivery(1));
        List<UUID> sent = new ArrayList<>();
        PosEventPublisher publisher = event -> sent.add(event.eventId());

        int count = dispatcher(store, publisher, 5).dispatchOnce();

        assertEquals(1, count);
        assertEquals(List.of(store.deliveries.get(0).event().eventId()), sent);
        assertEquals(1, store.published);
        assertEquals(0, store.rescheduled);
    }

    @Test
    void retriesFailureWithExponentialBackoff() {
        FakeStore store = new FakeStore();
        store.deliveries.add(delivery(2));
        PosOutboxDispatcher dispatcher = dispatcher(store, event -> { throw new IllegalStateException("broker down"); }, 5);

        assertEquals(0, dispatcher.dispatchOnce());
        assertEquals(1, store.rescheduled);
        assertFalse(store.dead);
        assertEquals(NOW.plusSeconds(20), store.availableAt);
        assertTrue(store.error.contains("broker down"));
    }

    @Test
    void deadLettersAfterMaximumAttempts() {
        FakeStore store = new FakeStore();
        store.deliveries.add(delivery(5));

        dispatcher(store, event -> { throw new IllegalStateException("permanent"); }, 5).dispatchOnce();

        assertEquals(1, store.rescheduled);
        assertTrue(store.dead);
    }

    private static PosOutboxDispatcher dispatcher(FakeStore store, PosEventPublisher publisher, int maxAttempts) {
        return new PosOutboxDispatcher(store, publisher,
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(30),
                Duration.ofSeconds(10), Duration.ofMinutes(5), 20, maxAttempts);
    }

    private static PosOutboxDelivery delivery(int attempts) {
        return new PosOutboxDelivery(new PosOutboxEvent(UUID.randomUUID(), UUID.randomUUID(),
                "Sale", UUID.randomUUID().toString(), "pos.sale.completed", 1, NOW, "{}"),
                attempts, UUID.randomUUID());
    }

    private static final class FakeStore implements PosOutboxDeliveryStore {
        final List<PosOutboxDelivery> deliveries = new ArrayList<>();
        int published;
        int rescheduled;
        boolean dead;
        Instant availableAt;
        String error;

        @Override public List<PosOutboxDelivery> claimDue(Instant now, Instant leaseUntil, int limit) {
            return List.copyOf(deliveries.subList(0, Math.min(limit, deliveries.size())));
        }
        @Override public boolean markPublished(UUID id, UUID token, Instant at) { published++; return true; }
        @Override public boolean reschedule(UUID id, UUID token, Instant at, String message, boolean isDead) {
            rescheduled++; availableAt = at; error = message; dead = isDead; return true;
        }
    }
}
