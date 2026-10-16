package tech.kayys.numou.construction.adapter.postgres.outbox;

import io.quarkus.test.junit.QuarkusTest;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.construction.spi.outbox.OutboxRelayStore;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Adapter-level tests for the actual PostgreSQL relay store. The test database must
 * have the bundle migrations applied; the fixture DDL is only a fallback for local
 * starter runs and must be reconciled with the production migration set.
 */
@QuarkusTest
class PgOutboxRelayStoreIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-14T00:00:00Z");

    @Inject PgPool pool;
    @Inject PgOutboxRelayStore store;

    @BeforeEach
    void ensureFixtureSchema() {
        pool.query("""
                CREATE TABLE IF NOT EXISTS construction_outbox_event (
                    event_id UUID PRIMARY KEY,
                    aggregate_type VARCHAR(128) NOT NULL,
                    aggregate_id UUID NOT NULL,
                    event_type VARCHAR(255) NOT NULL,
                    payload JSONB NOT NULL,
                    occurred_at TIMESTAMPTZ NOT NULL,
                    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
                    attempts INTEGER NOT NULL DEFAULT 0,
                    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                    published_at TIMESTAMPTZ NULL,
                    last_error TEXT NULL,
                    tenant_id VARCHAR(100) NULL,
                    worker_id VARCHAR(160) NULL,
                    lease_token UUID NULL,
                    locked_until TIMESTAMPTZ NULL,
                    terminal BOOLEAN NOT NULL DEFAULT FALSE,
                    max_attempts INTEGER NOT NULL DEFAULT 12
                )
                """).execute().await().indefinitely();
        pool.query("ALTER TABLE construction_outbox_event ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(100) NULL, ADD COLUMN IF NOT EXISTS worker_id VARCHAR(160) NULL, ADD COLUMN IF NOT EXISTS lease_token UUID NULL, ADD COLUMN IF NOT EXISTS locked_until TIMESTAMPTZ NULL, ADD COLUMN IF NOT EXISTS terminal BOOLEAN NOT NULL DEFAULT FALSE, ADD COLUMN IF NOT EXISTS max_attempts INTEGER NOT NULL DEFAULT 12")
                .execute().await().indefinitely();
        pool.query("DELETE FROM construction_outbox_event WHERE event_type = 'IntegrationTestEvent'")
                .execute().await().indefinitely();
    }

    @Test
    void claimsEligibleRowsAndPersistsLeaseIdentity() {
        UUID aggregate = UUID.randomUUID();
        UUID eligible = insert(aggregate, "PENDING", NOW.minusSeconds(1), null, null, false);
        insert(aggregate, "PENDING", NOW.plusSeconds(60), null, null, false);
        insert(aggregate, "FAILED", NOW.minusSeconds(1), null, null, true);
        insert(aggregate, "PUBLISHING", NOW.plusSeconds(60), UUID.randomUUID(), NOW.plusSeconds(90), false);

        List<OutboxRelayStore.ClaimedOutboxEvent> claimed = store.claimBatch(
                "worker-integration", NOW, NOW.plusSeconds(30), 10).await().indefinitely();

        assertEquals(1, claimed.size());
        var event = claimed.get(0);
        assertEquals(eligible, event.eventId());
        assertEquals(1, event.attempts());
        assertNotNull(event.leaseToken());
        Row row = row(eligible);
        assertEquals("PUBLISHING", row.getString("status"));
        assertEquals("worker-integration", row.getString("worker_id"));
        assertEquals(event.leaseToken(), row.getUUID("lease_token"));
    }

    @Test
    void staleLeaseCannotAcknowledgeAfterExpiredEventIsReclaimed() {
        UUID aggregate = UUID.randomUUID();
        UUID eventId = insert(aggregate, "PUBLISHING", NOW.minusSeconds(5), UUID.randomUUID(), NOW.minusSeconds(1), false);
        UUID staleToken = row(eventId).getUUID("lease_token");

        var reclaimed = store.claimBatch("worker-B", NOW, NOW.plusSeconds(30), 10)
                .await().indefinitely();
        assertEquals(1, reclaimed.size());
        UUID currentToken = reclaimed.get(0).leaseToken();
        assertNotEquals(staleToken, currentToken);
        assertFalse(store.markPublished(eventId, staleToken, NOW).await().indefinitely());
        assertEquals("PUBLISHING", row(eventId).getString("status"));
        assertEquals(currentToken, row(eventId).getUUID("lease_token"));
        assertTrue(store.markPublished(eventId, currentToken, NOW).await().indefinitely());
        assertEquals("PUBLISHED", row(eventId).getString("status"));
        assertNull(row(eventId).getUUID("lease_token"));
    }

    @Test
    void failedTransitionIsLeaseFencedAndBoundsStoredError() {
        UUID aggregate = UUID.randomUUID();
        UUID eventId = insert(aggregate, "PENDING", NOW.minusSeconds(1), null, null, false);
        var claimed = store.claimBatch("worker-failure", NOW, NOW.plusSeconds(30), 1)
                .await().indefinitely().get(0);
        UUID wrongToken = UUID.randomUUID();
        assertFalse(store.markFailed(eventId, wrongToken, NOW.plusSeconds(5), "wrong", false)
                .await().indefinitely());
        assertTrue(store.markFailed(eventId, claimed.leaseToken(), NOW.plusSeconds(5), "x".repeat(2500), true)
                .await().indefinitely());
        Row row = row(eventId);
        assertEquals("FAILED", row.getString("status"));
        assertEquals(2000, row.getString("last_error").length());
        assertTrue(row.getBoolean("terminal"));
        assertNull(row.getUUID("lease_token"));
    }

    @Test
    void rejectsInvalidClaimArgumentsBeforeIssuingSql() {
        assertThrows(RuntimeException.class, () -> store.claimBatch(" ", NOW, NOW.plusSeconds(1), 1)
                .await().indefinitely());
        assertThrows(RuntimeException.class, () -> store.claimBatch("worker", NOW, NOW, 1)
                .await().indefinitely());
        assertThrows(RuntimeException.class, () -> store.claimBatch("worker", NOW, NOW.plusSeconds(1), 501)
                .await().indefinitely());
    }

    private UUID insert(UUID aggregate, String status, Instant availableAt,
                        UUID leaseToken, Instant lockedUntil, boolean terminal) {
        UUID eventId = UUID.randomUUID();
        pool.preparedQuery("""
                INSERT INTO construction_outbox_event
                    (event_id, aggregate_type, aggregate_id, event_type, payload, occurred_at,
                     status, attempts, available_at, tenant_id, worker_id, lease_token, locked_until, terminal)
                VALUES ($1, 'BOQ', $2, 'IntegrationTestEvent', '{\"test\":true}'::jsonb, $3,
                        $4, 0, $5, 'tenant-integration', CASE WHEN $6::uuid IS NULL THEN NULL ELSE 'stale-worker' END,
                        $6, $7, $8)
                """)
                .execute(Tuple.tuple()
                        .addValue(eventId)
                        .addValue(aggregate)
                        .addValue(NOW.minusSeconds(10))
                        .addValue(status)
                        .addValue(availableAt)
                        .addValue(leaseToken)
                        .addValue(lockedUntil)
                        .addValue(terminal)).await().indefinitely();
        return eventId;
    }

    private Row row(UUID eventId) {
        return pool.preparedQuery("SELECT status, attempts, worker_id, lease_token, locked_until, last_error, terminal FROM construction_outbox_event WHERE event_id=$1")
                .execute(Tuple.of(eventId)).await().indefinitely().iterator().next();
    }
}
