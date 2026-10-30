package tech.kayys.numou.queuemanagement.postgres;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.queuemanagement.domain.QueueDefinition;
import tech.kayys.numou.queuemanagement.domain.QueueEntry;
import tech.kayys.numou.queuemanagement.domain.QueueEntryId;
import tech.kayys.numou.queuemanagement.domain.QueueOrderingPolicy;
import tech.kayys.numou.queuemanagement.domain.QueueProfile;
import tech.kayys.numou.queuemanagement.domain.QueueSourceReference;
import tech.kayys.numou.queuemanagement.domain.QueueId;
import tech.kayys.numou.queuemanagement.spi.QueueAuditRecord;
import tech.kayys.numou.queuemanagement.spi.QueueCommandContext;
import tech.kayys.numou.queuemanagement.spi.QueueCommandResult;
import tech.kayys.numou.queuemanagement.spi.QueueEventRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(QueuePostgresQuarkusTestResource.class)
class QueuePostgresCommandTransactionQuarkusTest {
    private static final TenantId TENANT = TenantId.of(
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    private static final QueueId QUEUE = QueueId.of(
            UUID.fromString("11111111-1111-1111-1111-111111111111"));

    @Inject QueuePostgresCommandTransactionAdapter transactions;
    @Inject AgroalDataSource dataSource;

    @Test
    void completedCommandIsDecodedAndReplayedWithoutExecutingWorkAgain() throws Exception {
        String key = "replay-" + UUID.randomUUID();
        QueueCommandContext context = context(key);
        AtomicInteger executions = new AtomicInteger();

        QueueEntry first = transactions.execute(context, "queue.join", "request-hash", () -> {
            executions.incrementAndGet();
            return result(context, UUID.randomUUID(), "{}");
        });
        QueueEntry replay = transactions.execute(context, "queue.join", "request-hash", () -> {
            executions.incrementAndGet();
            return result(context, UUID.randomUUID(), "{}");
        });

        assertEquals(1, executions.get());
        assertEquals(first.id(), replay.id());
        assertEquals(first.tenantId(), replay.tenantId());
        assertEquals(first.status(), replay.status());
        assertEquals(1, count("queue_idempotency", "idempotency_key", key));
        assertEquals(1, count("queue_audit", "correlation_id", context.correlationId()));
        assertEquals(1, count("queue_outbox", "correlation_id", context.correlationId()));
    }

    @Test
    void concurrentSameKeyCommandsExecuteBusinessWorkOnlyOnce() throws Exception {
        String key = "concurrent-" + UUID.randomUUID();
        QueueCommandContext context = context(key);
        AtomicInteger executions = new AtomicInteger();
        CountDownLatch firstWorkEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstWork = new CountDownLatch(1);
        CountDownLatch secondCallerStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<QueueEntry> first = executor.submit(() ->
                    transactions.execute(context, "queue.join", "concurrent-hash", () -> {
                        executions.incrementAndGet();
                        firstWorkEntered.countDown();
                        await(releaseFirstWork);
                        return result(context, UUID.randomUUID(), "{}");
                    }));
            assertTrue(firstWorkEntered.await(10, TimeUnit.SECONDS),
                    "first command should enter its transactional work");

            Future<QueueEntry> second = executor.submit(() -> {
                secondCallerStarted.countDown();
                return transactions.execute(context, "queue.join", "concurrent-hash", () -> {
                    executions.incrementAndGet();
                    return result(context, UUID.randomUUID(), "{}");
                });
            });
            assertTrue(secondCallerStarted.await(10, TimeUnit.SECONDS),
                    "second command should start while the first transaction is open");
            awaitIdempotencyInsertToBlock();
            releaseFirstWork.countDown();

            QueueEntry firstResult = first.get(20, TimeUnit.SECONDS);
            QueueEntry secondResult = second.get(20, TimeUnit.SECONDS);
            assertEquals(1, executions.get());
            assertEquals(firstResult.id(), secondResult.id());
            assertEquals(1, count("queue_idempotency", "idempotency_key", key));
            assertEquals(1, count("queue_audit", "correlation_id", context.correlationId()));
            assertEquals(1, count("queue_outbox", "correlation_id", context.correlationId()));
        } finally {
            releaseFirstWork.countDown();
            executor.shutdownNow();
        }
    }

    /**
     * Do not release the first transaction merely because the second thread was
     * scheduled. Observe PostgreSQL's unique-key conflict wait so the test proves
     * the second reservation INSERT is actually blocked behind the first commit.
     */
    private void awaitIdempotencyInsertToBlock() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        String sql = "SELECT EXISTS (SELECT 1 FROM pg_stat_activity "
                + "WHERE pid <> pg_backend_pid() AND usename = current_user "
                + "AND wait_event_type = 'Lock' "
                + "AND query LIKE 'INSERT INTO queue_idempotency%')";
        while (System.nanoTime() < deadline) {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet rs = statement.executeQuery()) {
                rs.next();
                if (rs.getBoolean(1)) return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError(
                "second command never reached PostgreSQL's idempotency unique-key lock");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release concurrent queue command");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating concurrent queue command", e);
        }
    }

    @Test
    void changedRequestHashIsRejectedBeforeWorkRuns() {
        String key = "conflict-" + UUID.randomUUID();
        QueueCommandContext context = context(key);
        transactions.execute(context, "queue.join", "first-hash",
                () -> result(context, UUID.randomUUID(), "{}"));

        AtomicInteger executions = new AtomicInteger();
        assertThrows(QueuePostgresCommandTransactionAdapter.IdempotencyConflictException.class,
                () -> transactions.execute(context, "queue.join", "different-hash", () -> {
                    executions.incrementAndGet();
                    return result(context, UUID.randomUUID(), "{}");
                }));
        assertEquals(0, executions.get());
    }

    @Test
    void outboxFailureRollsBackTheCommandAuditAndIdempotencyReservation() throws Exception {
        String key = "rollback-" + UUID.randomUUID();
        QueueCommandContext context = context(key);

        assertThrows(IllegalStateException.class, () ->
                transactions.execute(context, "queue.join", "rollback-hash",
                        () -> result(context, UUID.randomUUID(), "{")));

        assertEquals(0, count("queue_idempotency", "idempotency_key", key));
        assertEquals(0, count("queue_audit", "correlation_id", context.correlationId()));
        assertEquals(0, count("queue_outbox", "correlation_id", context.correlationId()));
        assertEquals(0, count("queue_entry", "source_resource_id", key));
    }

    @Test
    void committedOrphanedInProgressReservationFailsClosed() throws Exception {
        String key = "orphan-" + UUID.randomUUID();
        QueueCommandContext context = context(key);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement tenant = connection.prepareStatement(
                     "SELECT set_config('numou.tenant_id', ?, false)")) {
            tenant.setString(1, TENANT.value().toString());
            tenant.execute();
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO queue_idempotency (tenant_id, operation_scope, idempotency_key, request_hash, state) "
                            + "VALUES (?, 'queue.join', ?, 'orphan-hash', 'IN_PROGRESS')")) {
                insert.setObject(1, TENANT.value());
                insert.setString(2, key);
                insert.executeUpdate();
            }
        }

        AtomicInteger executions = new AtomicInteger();
        assertThrows(IllegalStateException.class, () ->
                transactions.execute(context, "queue.join", "orphan-hash", () -> {
                    executions.incrementAndGet();
                    return result(context, UUID.randomUUID(), "{}");
                }));
        assertEquals(0, executions.get());
        assertEquals(1, count("queue_idempotency", "idempotency_key", key));
    }

    private QueueCommandResult<QueueEntry> result(
            QueueCommandContext context, UUID entryId, String eventPayload) {
        Instant now = context.occurredAt();
        QueueDefinition queue = QueueDefinition.create(QUEUE, TENANT, "main", "Main Queue",
                QueueProfile.WAITING_SERVICE, QueueOrderingPolicy.FIFO);
        QueueEntry entry = QueueEntry.join(QueueEntryId.of(entryId), queue,
                new QueueSourceReference("queue-test", "test-command", context.idempotencyKey()), 0, now);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement tenantSetting = connection.prepareStatement(
                     "SELECT set_config('numou.tenant_id', ?, true)");
             PreparedStatement queueInsert = connection.prepareStatement(
                     "INSERT INTO queue_definition (tenant_id, queue_id, code, display_name, profile, ordering_policy, status) "
                             + "VALUES (?, ?, 'main', 'Main Queue', 'WAITING_SERVICE', 'FIFO', 'ACTIVE') "
                             + "ON CONFLICT (tenant_id, queue_id) DO NOTHING");
             PreparedStatement entryInsert = connection.prepareStatement(
                     "INSERT INTO queue_entry (tenant_id, queue_id, entry_id, profile, source_capability, "
                             + "source_resource_type, source_resource_id, priority, status, entered_at, updated_at) "
                             + "VALUES (?, ?, ?, 'WAITING_SERVICE', 'queue-test', 'test-command', ?, 0, 'WAITING', ?, ?)")) {
            tenantSetting.setString(1, TENANT.value().toString());
            tenantSetting.execute();
            queueInsert.setObject(1, TENANT.value());
            queueInsert.setObject(2, QUEUE.value());
            queueInsert.executeUpdate();
            entryInsert.setObject(1, TENANT.value());
            entryInsert.setObject(2, QUEUE.value());
            entryInsert.setObject(3, entryId);
            entryInsert.setString(4, context.idempotencyKey());
            entryInsert.setObject(5, java.sql.Timestamp.from(now));
            entryInsert.setObject(6, java.sql.Timestamp.from(now));
            entryInsert.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to persist test queue mutation", e);
        }

        QueueAuditRecord audit = new QueueAuditRecord(TENANT, context.actorId(), "QUEUE_JOINED",
                "QUEUE_ENTRY", entryId.toString(), context.correlationId(), now);
        QueueEventRecord event = new QueueEventRecord(TENANT, UUID.randomUUID().toString(),
                "QueueEntryJoined", 1, "QUEUE_ENTRY", entryId.toString(),
                context.correlationId(), now, eventPayload);
        return new QueueCommandResult<>(entry, List.of(audit), List.of(event));
    }

    private static QueueCommandContext context(String key) {
        return new QueueCommandContext(TENANT, "queue-test-actor", "corr-" + key, key, Instant.now());
    }

    private int count(String table, String column, String value) throws Exception {
        if (!List.of("queue_idempotency", "queue_audit", "queue_outbox", "queue_entry").contains(table)
                || !List.of("idempotency_key", "correlation_id", "source_resource_id").contains(column)) {
            throw new IllegalArgumentException("Unsupported test count identifier");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement tenant = connection.prepareStatement(
                     "SELECT set_config('numou.tenant_id', ?, false)")) {
            tenant.setString(1, TENANT.value().toString());
            tenant.execute();
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT count(*) FROM " + table + " WHERE tenant_id = ? AND " + column + " = ?")) {
                query.setObject(1, TENANT.value());
                query.setString(2, value);
                try (ResultSet rs = query.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        }
    }
}
