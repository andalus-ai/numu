package tech.kayys.numou.affiliate.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.affiliate.spi.AffiliateEventOutboxStore;
import tech.kayys.numou.affiliate.spi.AffiliateEventOutboxStore.DeadLetterCursor;
import tech.kayys.numou.affiliate.spi.AffiliateEventOutboxStore.DeadLetterPage;
import tech.kayys.numou.affiliate.spi.AffiliateEventOutboxStore.DeadLetterSummary;
import tech.kayys.numou.affiliate.spi.AffiliateEventOutboxStore.OutboxHealthSnapshot;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class AffiliateOutboxOperationsTest {
    @Test
    void redriveUsesAuthenticatedTenantActorAndAuditContext() {
        RecordingStore store = new RecordingStore();
        AffiliateOutboxOperations operations = new AffiliateOutboxOperations(store,
                Clock.fixed(Instant.parse("2026-10-31T10:00:00Z"), ZoneOffset.UTC));
        AffiliateCommandContext context = new AffiliateCommandContext(
                "tenant-7", "operator-42", "redrive-req-1", "corr-9");
        UUID eventId = UUID.randomUUID();

        assertTrue(operations.redriveDeadLetter(context, eventId, "provider incident resolved")
                .toCompletableFuture().join());
        assertEquals("tenant-7", store.tenant);
        assertEquals("operator-42", store.actor);
        assertEquals(eventId, store.eventId);
        assertEquals("provider incident resolved", store.reason);
        assertEquals("redrive-req-1", store.idempotencyKey);
        assertEquals("corr-9", store.correlationId);
        assertEquals(Instant.parse("2026-10-31T10:00:00Z"), store.now);
    }

    @Test
    void listsDeadLettersOnlyWithinTheAuthorizedTenantAndUsesKeysetCursor() {
        RecordingStore store = new RecordingStore();
        AffiliateOutboxOperations operations = new AffiliateOutboxOperations(store, Clock.systemUTC());
        AffiliateCommandContext context = new AffiliateCommandContext("tenant-7", "operator-42", "req", "corr");
        Instant occurredAt = Instant.parse("2026-10-31T09:00:00Z");
        DeadLetterCursor cursor = new DeadLetterCursor(occurredAt, UUID.fromString("00000000-0000-0000-0000-000000000007"));
        DeadLetterSummary summary = new DeadLetterSummary(UUID.randomUUID(), "PartnerPayout",
                UUID.randomUUID(), "affiliate.payout.submitted", 5, occurredAt, 9L);
        store.deadLetterPage = new DeadLetterPage(List.of(summary), null);

        assertEquals(store.deadLetterPage, operations.listDeadLetters(context, cursor, 25)
                .toCompletableFuture().join());
        assertEquals("tenant-7", store.tenant);
        assertEquals(cursor, store.cursor);
        assertEquals(25, store.limit);
    }

    @Test
    void rejectsUnboundedOrEmptyDeadLetterPages() {
        AffiliateOutboxOperations operations = new AffiliateOutboxOperations(
                new RecordingStore(), Clock.systemUTC());
        AffiliateCommandContext context = new AffiliateCommandContext("tenant-7", "operator-42", "req", "corr");

        assertThrows(IllegalArgumentException.class,
                () -> operations.listDeadLetters(context, null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> operations.listDeadLetters(context, null, 101));
    }

    @Test
    void exposesAggregateHealthOnlyWithPlatformOperationsContext() {
        RecordingStore store = new RecordingStore();
        AffiliateOutboxOperations operations = new AffiliateOutboxOperations(store, Clock.systemUTC());
        OutboxHealthSnapshot expected = new OutboxHealthSnapshot(12, 9, 3, 1, 2, 45);
        store.health = expected;

        assertEquals(expected, operations.healthSnapshot(new AffiliatePlatformOperationsContext("platform-operator"))
                .toCompletableFuture().join());
        assertThrows(NullPointerException.class, () -> operations.healthSnapshot(null));
    }

    @Test
    void rejectsMissingOrOversizedOperatorReason() {
        AffiliateOutboxOperations operations = new AffiliateOutboxOperations(new RecordingStore(), Clock.systemUTC());
        AffiliateCommandContext context = new AffiliateCommandContext("tenant-7", "operator-42", "req", "corr");
        assertThrows(IllegalArgumentException.class,
                () -> operations.redriveDeadLetter(context, UUID.randomUUID(), " "));
        assertThrows(IllegalArgumentException.class,
                () -> operations.redriveDeadLetter(context, UUID.randomUUID(), "x".repeat(1001)));
    }

    private static final class RecordingStore implements AffiliateEventOutboxStore {
        String tenant, actor, reason, idempotencyKey, correlationId;
        UUID eventId;
        Instant now;
        DeadLetterCursor cursor;
        int limit;
        DeadLetterPage deadLetterPage = new DeadLetterPage(List.of(), null);
        OutboxHealthSnapshot health = new OutboxHealthSnapshot(0, 0, 0, 0, 0, 0);

        @Override public java.util.concurrent.CompletionStage<java.util.List<ClaimedEvent>> claimPending(
                String tenantId, String workerId, Instant now, java.time.Duration lease, int limit) {
            return CompletableFuture.completedFuture(java.util.List.of());
        }
        @Override public java.util.concurrent.CompletionStage<Void> markDelivered(
                String tenantId, UUID eventId, String workerId, Instant now) {
            return CompletableFuture.completedFuture(null);
        }
        @Override public java.util.concurrent.CompletionStage<Void> reschedule(
                String tenantId, UUID eventId, String workerId, String reason,
                Instant retryAt, Instant now, int maxAttempts) {
            return CompletableFuture.completedFuture(null);
        }
        @Override public java.util.concurrent.CompletionStage<Boolean> redriveDeadLetter(
                String tenantId, UUID eventId, String actorId, String reason,
                String idempotencyKey, String correlationId, Instant now) {
            this.tenant=tenantId; this.eventId=eventId; this.actor=actorId; this.reason=reason;
            this.idempotencyKey=idempotencyKey; this.correlationId=correlationId; this.now=now;
            return CompletableFuture.completedFuture(true);
        }
        @Override public java.util.concurrent.CompletionStage<DeadLetterPage> findDeadLetters(
                String tenantId, DeadLetterCursor before, int limit) {
            this.tenant = tenantId; this.cursor = before; this.limit = limit;
            return CompletableFuture.completedFuture(deadLetterPage);
        }
        @Override public java.util.concurrent.CompletionStage<OutboxHealthSnapshot> healthSnapshot() {
            return CompletableFuture.completedFuture(health);
        }
    }
}
