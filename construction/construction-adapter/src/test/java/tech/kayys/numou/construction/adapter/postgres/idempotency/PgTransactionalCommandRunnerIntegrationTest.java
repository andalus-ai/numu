package tech.kayys.numou.construction.adapter.postgres.idempotency;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.construction.application.idempotency.CommandOutcomeCodec;
import tech.kayys.numou.construction.application.idempotency.IdempotentCommandExecutor;
import tech.kayys.numou.construction.domain.boq.BoqCalculationPolicy;
import tech.kayys.numou.construction.domain.boq.BoqRevisionSnapshot;
import tech.kayys.numou.construction.spi.idempotency.CommandIdempotencyStore;
import tech.kayys.numou.construction.spi.idempotency.TransactionalCommandContext;
import tech.kayys.numou.construction.spi.outbox.TransactionalOutboxEvent;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the actual PgTransactionalCommandRunner and its
 * connection-bound PgTransactionalCommandContext. Requires the bundle migrations
 * to be applied to the Quarkus test database before the test suite starts.
 */
@QuarkusTest
class PgTransactionalCommandRunnerIntegrationTest {
    @Inject PgPool pool;

    private PgTransactionalCommandRunner runner;

    @BeforeEach
    void createRunner() {
        runner = new PgTransactionalCommandRunner(pool, connection -> new TransactionalCommandContext.BoqRevisionOperations() {
            @Override public Uni<BoqRevisionSnapshot> captureDraft(String tenantId, UUID boqId, UUID actorId,
                    BoqCalculationPolicy policy, Instant now) {
                return Uni.createFrom().failure(new UnsupportedOperationException("not used by this test"));
            }
            @Override public Uni<BoqRevisionSnapshot> submitForReview(String tenantId, UUID boqId,
                    int revisionNumber, UUID actorId, Instant now) {
                return Uni.createFrom().failure(new UnsupportedOperationException("not used by this test"));
            }
            @Override public Uni<BoqRevisionSnapshot> approve(String tenantId, UUID boqId,
                    int revisionNumber, UUID actorId, Instant now) {
                return Uni.createFrom().failure(new UnsupportedOperationException("not used by this test"));
            }
        });
    }

    @Test
    void failedCallbackRollsBackReservationAndOutboxTogether() {
        String tenant = "it-rollback-" + UUID.randomUUID();
        String key = "key-" + UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        var reservation = new CommandIdempotencyStore.Reservation(tenant, key, "boq.capture",
                "a".repeat(64), now);
        var event = new TransactionalOutboxEvent(eventId, tenant, "BOQ", UUID.randomUUID(),
                "BoqRevisionCaptured", "{\"test\":true}", now);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                runner.inTransaction(context -> context.reserve(reservation)
                        .flatMap(result -> context.appendOutboxEvent(event))
                        .flatMap(ignored -> Uni.createFrom().failure(new IllegalStateException("injected failure"))))
                        .await().indefinitely());
        assertEquals("injected failure", thrown.getMessage());
        assertEquals(0L, countIdempotency(tenant, key));
        assertEquals(0L, countOutbox(eventId));
    }

    @Test
    void concurrentSameKeyCommandsExecuteBusinessCallbackAndAppendEventOnce() {
        String tenant = "it-race-" + UUID.randomUUID();
        String key = "key-" + UUID.randomUUID();
        String operation = "boq.capture";
        String fingerprint = "b".repeat(64);
        Instant now = Instant.now();
        AtomicInteger executions = new AtomicInteger();
        IdempotentCommandExecutor executor = new IdempotentCommandExecutor(runner, new StringCodec());

        java.util.function.Supplier<Uni<String>> request = () -> executor.execute(
                tenant, key, operation, fingerprint, now, String.class, context -> {
                    executions.incrementAndGet();
                    var event = new TransactionalOutboxEvent(UUID.randomUUID(), tenant, "BOQ",
                            UUID.randomUUID(), "BoqRevisionCaptured", "{\"result\":\"ok\"}", now);
                    return context.appendOutboxEvent(event).replaceWith("ok");
                });

        var results = Uni.combine().all().unis(request.get(), request.get()).asTuple().await().indefinitely();
        assertEquals("ok", results.getItem1());
        assertEquals("ok", results.getItem2());
        assertEquals(1, executions.get(), "only the reservation winner may execute the callback");
        assertEquals(1L, countIdempotency(tenant, key));
        assertEquals(1L, countOutboxForTenant(tenant));
    }

    private long countIdempotency(String tenant, String key) {
        Row row = pool.preparedQuery("SELECT count(*) AS n FROM construction_command_idempotency WHERE tenant_id=$1 AND idempotency_key=$2")
                .execute(Tuple.of(tenant, key)).await().indefinitely().iterator().next();
        return row.getLong("n");
    }

    private long countOutbox(UUID eventId) {
        Row row = pool.preparedQuery("SELECT count(*) AS n FROM construction_outbox_event WHERE event_id=$1")
                .execute(Tuple.of(eventId)).await().indefinitely().iterator().next();
        return row.getLong("n");
    }

    private long countOutboxForTenant(String tenant) {
        Row row = pool.preparedQuery("SELECT count(*) AS n FROM construction_outbox_event WHERE tenant_id=$1 AND event_type='BoqRevisionCaptured'")
                .execute(Tuple.of(tenant)).await().indefinitely().iterator().next();
        return row.getLong("n");
    }

    private static final class StringCodec implements CommandOutcomeCodec {
        @Override public String encode(Object value) {
            String text = String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\"");
            return "\"" + text + "\"";
        }
        @Override public <T> T decode(String json, Class<T> resultType) {
            if (resultType != String.class || json == null || json.length() < 2) {
                throw new IllegalArgumentException("test codec only supports JSON strings");
            }
            return resultType.cast(json.substring(1, json.length() - 1)
                    .replace("\\\"", "\"").replace("\\\\", "\\"));
        }
    }
}
