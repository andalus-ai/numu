package tech.kayys.numou.affiliate.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.affiliate.spi.AffiliateEventOutboxStore;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
    }
}
