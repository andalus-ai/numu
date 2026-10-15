package tech.kayys.syirkah.construction.application.outbox;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.construction.spi.outbox.OutboxDeliveryPort;
import tech.kayys.syirkah.construction.spi.outbox.OutboxRelayStore;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OutboxRelayServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-14T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void emptyBatchDoesNotInvokeDeliveryAndReturnsZero() {
        FakeStore store = new FakeStore(List.of());
        OutboxRelayService service = service(store, event -> {
            fail("delivery must not run for an empty batch");
            return Uni.createFrom().voidItem();
        });

        assertEquals(0, service.runOnce().await().indefinitely());
        assertTrue(store.published.isEmpty());
        assertTrue(store.failed.isEmpty());
        assertEquals("worker-test", store.workerId);
        assertEquals(NOW, store.claimedAt);
        assertEquals(NOW.plusSeconds(30), store.leaseUntil);
        assertEquals(10, store.limit);
    }

    @Test
    void successfulDeliveryAcknowledgesUsingEventLeaseToken() {
        OutboxRelayStore.ClaimedOutboxEvent event = event(1, 1);
        FakeStore store = new FakeStore(List.of(event));
        List<OutboxRelayStore.ClaimedOutboxEvent> delivered = new ArrayList<>();
        OutboxRelayService service = service(store, claimed -> {
            delivered.add(claimed);
            return Uni.createFrom().voidItem();
        });

        assertEquals(1, service.runOnce().await().indefinitely());
        assertEquals(List.of(event), delivered);
        assertEquals(1, store.published.size());
        assertEquals(event.eventId(), store.published.get(0).eventId());
        assertEquals(event.leaseToken(), store.published.get(0).leaseToken());
        assertEquals(NOW, store.published.get(0).publishedAt());
        assertTrue(store.failed.isEmpty());
    }

    @Test
    void deliveryFailureSchedulesRetryAndBoundsStoredError() {
        OutboxRelayStore.ClaimedOutboxEvent event = event(2, 2);
        FakeStore store = new FakeStore(List.of(event));
        String longMessage = "x".repeat(2500);
        OutboxRelayService service = service(store, claimed -> Uni.createFrom().failure(new IllegalStateException(longMessage)));

        assertEquals(1, service.runOnce().await().indefinitely());
        assertTrue(store.published.isEmpty());
        assertEquals(1, store.failed.size());
        FailedCall failure = store.failed.get(0);
        assertEquals(event.eventId(), failure.eventId());
        assertEquals(event.leaseToken(), failure.leaseToken());
        assertEquals(NOW.plusSeconds(4), failure.retryAt());
        assertEquals(2000, failure.error().length());
        assertFalse(failure.terminal());
    }

    @Test
    void exhaustedAttemptIsMarkedTerminalAndNotScheduledForRetry() {
        OutboxRelayStore.ClaimedOutboxEvent event = event(3, 3);
        FakeStore store = new FakeStore(List.of(event));
        OutboxRelayService service = service(store, claimed -> Uni.createFrom().failure(new RuntimeException("broker down")));

        assertEquals(1, service.runOnce().await().indefinitely());
        assertEquals(1, store.failed.size());
        assertEquals(NOW, store.failed.get(0).retryAt());
        assertTrue(store.failed.get(0).terminal());
        assertEquals("broker down", store.failed.get(0).error());
    }

    @Test
    void independentEventsCanSucceedAndFailWithinSameBatch() {
        OutboxRelayStore.ClaimedOutboxEvent good = event(4, 1);
        OutboxRelayStore.ClaimedOutboxEvent bad = event(5, 1);
        FakeStore store = new FakeStore(List.of(good, bad));
        OutboxDeliveryPort delivery = claimed -> claimed.eventId().equals(good.eventId())
                ? Uni.createFrom().voidItem()
                : Uni.createFrom().failure(new IllegalArgumentException("bad payload"));
        OutboxRelayService service = service(store, delivery);

        assertEquals(2, service.runOnce().await().indefinitely());
        assertEquals(1, store.published.size());
        assertEquals(good.eventId(), store.published.get(0).eventId());
        assertEquals(1, store.failed.size());
        assertEquals(bad.eventId(), store.failed.get(0).eventId());
        assertEquals("bad payload", store.failed.get(0).error());
    }

    @Test
    void rejectsInvalidConfiguration() {
        FakeStore store = new FakeStore(List.of());
        OutboxDeliveryPort delivery = event -> Uni.createFrom().voidItem();
        OutboxRetryPolicy policy = new OutboxRetryPolicy(Duration.ofSeconds(1), Duration.ofSeconds(5), 3);
        assertThrows(IllegalArgumentException.class, () -> new OutboxRelayService(
                store, delivery, policy, CLOCK, Duration.ZERO, 1, "worker"));
        assertThrows(IllegalArgumentException.class, () -> new OutboxRelayService(
                store, delivery, policy, CLOCK, Duration.ofSeconds(1), 0, "worker"));
        assertThrows(IllegalArgumentException.class, () -> new OutboxRelayService(
                store, delivery, policy, CLOCK, Duration.ofSeconds(1), 1, " "));
    }

    private static OutboxRelayService service(FakeStore store, OutboxDeliveryPort delivery) {
        return new OutboxRelayService(store, delivery,
                new OutboxRetryPolicy(Duration.ofSeconds(2), Duration.ofSeconds(10), 3),
                CLOCK, Duration.ofSeconds(30), 10, "worker-test");
    }

    private static OutboxRelayStore.ClaimedOutboxEvent event(int id, int attempts) {
        return new OutboxRelayStore.ClaimedOutboxEvent(
                UUID.nameUUIDFromBytes(("event-" + id).getBytes()), "BOQ", UUID.nameUUIDFromBytes(("aggregate-" + id).getBytes()),
                "BoqRevisionCaptured", "{}", NOW.minusSeconds(5), attempts,
                UUID.nameUUIDFromBytes(("lease-" + id).getBytes()));
    }

    private static final class FakeStore implements OutboxRelayStore {
        private final List<ClaimedOutboxEvent> batch;
        private final List<PublishedCall> published = new ArrayList<>();
        private final List<FailedCall> failed = new ArrayList<>();
        private String workerId;
        private Instant claimedAt;
        private Instant leaseUntil;
        private int limit;

        private FakeStore(List<ClaimedOutboxEvent> batch) { this.batch = batch; }

        @Override
        public Uni<List<ClaimedOutboxEvent>> claimBatch(String workerId, Instant now, Instant leaseUntil, int limit) {
            this.workerId = workerId;
            this.claimedAt = now;
            this.leaseUntil = leaseUntil;
            this.limit = limit;
            return Uni.createFrom().item(batch);
        }

        @Override
        public Uni<Boolean> markPublished(UUID eventId, UUID leaseToken, Instant publishedAt) {
            published.add(new PublishedCall(eventId, leaseToken, publishedAt));
            return Uni.createFrom().item(true);
        }

        @Override
        public Uni<Boolean> markFailed(UUID eventId, UUID leaseToken, Instant retryAt, String error, boolean terminal) {
            failed.add(new FailedCall(eventId, leaseToken, retryAt, error, terminal));
            return Uni.createFrom().item(true);
        }
    }

    private record PublishedCall(UUID eventId, UUID leaseToken, Instant publishedAt) { }
    private record FailedCall(UUID eventId, UUID leaseToken, Instant retryAt, String error, boolean terminal) { }
}
