package tech.kayys.numou.affiliate.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.affiliate.spi.AffiliateEventOutboxStore;
import tech.kayys.numou.affiliate.spi.AffiliateEventPublisher;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class AffiliateOutboxDispatcherTest {
    private static final Instant NOW = Instant.parse("2026-10-31T10:00:00Z");

    @Test
    void publishesAndAcknowledgesClaimedEvents() {
        FakeStore store = new FakeStore(List.of(event(1)));
        AffiliateEventPublisher publisher = ignored -> CompletableFuture.completedFuture(null);
        AffiliateOutboxDispatcher dispatcher = dispatcher(store, publisher);

        assertEquals(1, dispatcher.dispatchOnce("tenant-a", "worker-a").toCompletableFuture().join());
        assertEquals(1, store.delivered.size());
        assertEquals("tenant-a", store.delivered.get(0).tenantId());
        assertTrue(store.rescheduled.isEmpty());
    }

    @Test
    void failedPublicationIsRescheduledWithBoundedBackoffAndAttemptLimit() {
        FakeStore store = new FakeStore(List.of(event(3)));
        AffiliateEventPublisher publisher = ignored ->
                CompletableFuture.failedFuture(new IllegalStateException("broker unavailable"));
        AffiliateOutboxDispatcher dispatcher = dispatcher(store, publisher);

        assertEquals(0, dispatcher.dispatchOnce("tenant-a", "worker-a").toCompletableFuture().join());
        assertEquals(1, store.rescheduled.size());
        Retry retry = store.rescheduled.get(0);
        assertEquals(3, retry.maxAttempts());
        assertEquals(NOW.plusSeconds(40), retry.retryAt());
        assertTrue(retry.reason().contains("broker unavailable"));
    }

    @Test
    void requiresTenantAndWorkerIdentity() {
        AffiliateOutboxDispatcher dispatcher = dispatcher(new FakeStore(List.of()),
                ignored -> CompletableFuture.completedFuture(null));
        CompletionException failure = assertThrows(CompletionException.class,
                () -> dispatcher.dispatchOnce(" ", "worker").toCompletableFuture().join());
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());

        CompletionException missingWorker = assertThrows(CompletionException.class,
                () -> dispatcher.dispatchOnce("tenant-a", " ").toCompletableFuture().join());
        assertInstanceOf(IllegalArgumentException.class, missingWorker.getCause());
    }

    private static AffiliateOutboxDispatcher dispatcher(FakeStore store, AffiliateEventPublisher publisher) {
        return new AffiliateOutboxDispatcher(store, publisher, Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ofSeconds(40), 20, 3);
    }

    private static AffiliateEventOutboxStore.ClaimedEvent event(int attempts) {
        return new AffiliateEventOutboxStore.ClaimedEvent("tenant-a", UUID.randomUUID(),
                "PartnerPayout", UUID.randomUUID(), "affiliate.payout.submitted",
                "event-key-" + attempts, "{}", attempts, "worker-a", NOW.plusSeconds(30));
    }

    private static final class FakeStore implements AffiliateEventOutboxStore {
        private final List<ClaimedEvent> events;
        private final java.util.ArrayList<ClaimedEvent> delivered = new java.util.ArrayList<>();
        private final java.util.ArrayList<Retry> rescheduled = new java.util.ArrayList<>();

        private FakeStore(List<ClaimedEvent> events) { this.events = events; }

        @Override public java.util.concurrent.CompletionStage<List<ClaimedEvent>> claimPending(
                String tenantId, String workerId, Instant now, Duration lease, int limit) {
            assertEquals("tenant-a", tenantId);
            assertEquals("worker-a", workerId);
            assertTrue(limit > 0);
            return CompletableFuture.completedFuture(events);
        }

        @Override public java.util.concurrent.CompletionStage<Boolean> redriveDeadLetter(
                String tenantId, UUID eventId, String actorId, String reason,
                String idempotencyKey, String correlationId, Instant now) {
            return CompletableFuture.completedFuture(false);
        }

        @Override public java.util.concurrent.CompletionStage<DeadLetterPage> findDeadLetters(
                String tenantId, DeadLetterCursor before, int limit) {
            return CompletableFuture.completedFuture(new DeadLetterPage(List.of(), null));
        }

        @Override public java.util.concurrent.CompletionStage<OutboxHealthSnapshot> healthSnapshot() {
            return CompletableFuture.completedFuture(new OutboxHealthSnapshot(0, 0, 0, 0, 0, 0));
        }

        @Override public java.util.concurrent.CompletionStage<Void> markDelivered(
                String tenantId, UUID eventId, String workerId, Instant now) {
            delivered.add(events.stream().filter(e -> e.id().equals(eventId)).findFirst().orElseThrow());
            return CompletableFuture.completedFuture(null);
        }

        @Override public java.util.concurrent.CompletionStage<Void> reschedule(
                String tenantId, UUID eventId, String workerId, String reason,
                Instant retryAt, Instant now, int maxAttempts) {
            rescheduled.add(new Retry(reason, retryAt, maxAttempts));
            return CompletableFuture.completedFuture(null);
        }
    }

    private record Retry(String reason, Instant retryAt, int maxAttempts) {}
}
