package tech.kayys.numou.feedback.application.outbox;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FeedbackOutboxRelayTest {
    private static final Instant NOW = Instant.parse("2026-11-11T11:00:00Z");

    private static FeedbackOutboxMessage message(int attempts) {
        return new FeedbackOutboxMessage(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "feedback.submitted", 1, "{\"feedbackId\":\"test\"}", NOW, attempts);
    }

    private static final class Store implements FeedbackOutboxStore {
        final List<FeedbackOutboxMessage> claimed = new ArrayList<>();
        final List<UUID> published = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        Instant retryAt;
        int maximumAttempts;

        @Override public List<FeedbackOutboxMessage> claimPending(Instant now, Instant leaseUntil, int limit) {
            assertEquals(NOW, now);
            assertEquals(NOW.plusSeconds(60), leaseUntil);
            assertTrue(limit <= 50);
            return List.copyOf(claimed);
        }
        @Override public void markPublished(UUID eventId, Instant publishedAt) { published.add(eventId); }
        @Override public void recordFailure(UUID eventId, String error, Instant nextAttemptAt, int maximumAttempts) {
            errors.add(error);
            retryAt = nextAttemptAt;
            this.maximumAttempts = maximumAttempts;
        }
    }

    @Test void publishesAndAcknowledgesOnlySuccessfulEvents() {
        Store store = new Store();
        var event = message(0);
        store.claimed.add(event);
        List<UUID> delivered = new ArrayList<>();
        var relay = new FeedbackOutboxRelay(store, item -> delivered.add(item.eventId()),
                50, 8, Duration.ofSeconds(60));

        assertEquals(1, relay.dispatchOnce(NOW));
        assertEquals(List.of(event.eventId()), delivered);
        assertEquals(List.of(event.eventId()), store.published);
        assertTrue(store.errors.isEmpty());
    }

    @Test void recordsFailureAndSchedulesBoundedRetryWithoutAcknowledging() {
        Store store = new Store();
        store.claimed.add(message(1));
        var relay = new FeedbackOutboxRelay(store, item -> { throw new IllegalStateException("broker unavailable"); },
                50, 8, Duration.ofSeconds(60));

        assertEquals(0, relay.dispatchOnce(NOW));
        assertTrue(store.published.isEmpty());
        assertEquals(1, store.errors.size());
        assertTrue(store.errors.get(0).contains("broker unavailable"));
        assertEquals(NOW.plusSeconds(15), store.retryAt);
        assertEquals(8, store.maximumAttempts);
    }

    @Test void capsRetryDelayAndRejectsInvalidConfiguration() {
        assertEquals(Duration.ofSeconds(5), FeedbackOutboxRelay.retryDelay(1));
        assertEquals(Duration.ofSeconds(15), FeedbackOutboxRelay.retryDelay(2));
        assertEquals(Duration.ofMinutes(5), FeedbackOutboxRelay.retryDelay(10));
        assertThrows(IllegalArgumentException.class, () -> FeedbackOutboxRelay.retryDelay(0));
        assertThrows(IllegalArgumentException.class,
                () -> new FeedbackOutboxRelay(new Store(), item -> {}, 0, 3, Duration.ofSeconds(30)));
    }
}
