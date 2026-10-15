package tech.kayys.syirkah.construction.adapter.postgres.boq;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.construction.adapter.postgres.idempotency.PgTransactionalCommandRunner;
import tech.kayys.syirkah.construction.domain.boq.BoqCalculationPolicy;
import tech.kayys.syirkah.construction.spi.boq.BoqRevisionAuthorization;
import tech.kayys.syirkah.construction.spi.idempotency.TransactionalCommandContext;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the actual revision operations factory against the configured PostgreSQL test database.
 * The live BOQ tables are test fixtures because the source bundle does not define Syirkah's physical schema.
 * Revision snapshot/counter/outbox tables must be created by the bundle migrations first.
 */
@QuarkusTest
class DefaultPgBoqRevisionOperationsFactoryIntegrationTest {
    @Inject PgPool pool;

    private PgTransactionalCommandRunner runner;
    private final PgLiveBoqSqlMapping mapping = new PgLiveBoqSqlMapping(
            "it_live_boq", "boq_id", "project_id", "tenant_id", "boq_name", "current_revision", "status", "changed_at",
            "it_live_boq_item", "item_id", "boq_id", "wbs_node_id", "item_code", "description", "item_type",
            "quantity", "quantity_unit", "rate_amount", "rate_unit", "currency_code");

    @BeforeEach
    void prepareFixtures() {
        pool.query("CREATE TABLE IF NOT EXISTS it_live_boq (boq_id UUID PRIMARY KEY, project_id UUID NOT NULL, tenant_id TEXT NOT NULL, boq_name TEXT NOT NULL, current_revision INTEGER NOT NULL, status TEXT NOT NULL, changed_at TIMESTAMPTZ NOT NULL)")
                .execute().await().indefinitely();
        pool.query("CREATE TABLE IF NOT EXISTS it_live_boq_item (item_id UUID PRIMARY KEY, boq_id UUID NOT NULL, wbs_node_id UUID NOT NULL, item_code TEXT NOT NULL, description TEXT NOT NULL, item_type TEXT NOT NULL, quantity NUMERIC(24,8) NOT NULL, quantity_unit TEXT NOT NULL, rate_amount NUMERIC(24,8) NOT NULL, rate_unit TEXT NOT NULL, currency_code CHAR(3) NOT NULL)")
                .execute().await().indefinitely();
        var authorization = (BoqRevisionAuthorization) (tenant, actor, boq, action) -> Uni.createFrom().voidItem();
        var operationsFactory = new DefaultPgBoqRevisionOperationsFactory(
                new MappedPgLiveBoqRevisionStoreFactory(mapping), authorization);
        runner = new PgTransactionalCommandRunner(pool, operationsFactory);
    }

    @Test
    void captureWritesSnapshotCounterLiveStateAndOutboxInOneTransaction() {
        Fixture f = insertFixture();
        Instant now = Instant.parse("2026-10-13T10:00:00Z");
        var policy = new BoqCalculationPolicy("test-v1", 2, RoundingMode.HALF_UP);

        var snapshot = runner.inTransaction(context -> context.boqRevisions()
                        .captureDraft(f.tenant(), f.boqId(), f.actorId(), policy, now))
                .await().indefinitely();

        assertEquals(1, snapshot.revisionNumber());
        assertEquals("10.00", snapshot.totalAmount().toPlainString());
        assertEquals(1L, count("SELECT count(*) AS n FROM construction_boq_revision_snapshot WHERE tenant_id=$1 AND boq_id=$2", f.tenant(), f.boqId()));
        assertEquals(1L, count("SELECT count(*) AS n FROM construction_boq_revision_item WHERE tenant_id=$1 AND boq_id=$2", f.tenant(), f.boqId()));
        assertEquals(1L, count("SELECT count(*) AS n FROM construction_boq_revision_counter WHERE tenant_id=$1 AND boq_id=$2", f.tenant(), f.boqId()));
        assertEquals(1L, count("SELECT count(*) AS n FROM construction_outbox_event WHERE tenant_id=$1 AND aggregate_id=$2 AND event_type='BoqRevisionCaptured'", f.tenant(), f.boqId()));
        Row live = pool.preparedQuery("SELECT current_revision, status FROM it_live_boq WHERE tenant_id=$1 AND boq_id=$2")
                .execute(Tuple.of(f.tenant(), f.boqId())).await().indefinitely().iterator().next();
        assertEquals(1, live.getInteger("current_revision"));
        assertEquals("DRAFT", live.getString("status"));
    }

    @Test
    void failureAfterCaptureRollsBackSnapshotItemCounterLiveStateAndOutbox() {
        Fixture f = insertFixture();
        Instant now = Instant.parse("2026-10-13T10:00:00Z");
        var policy = new BoqCalculationPolicy("test-v1", 2, RoundingMode.HALF_UP);

        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                runner.inTransaction(context -> context.boqRevisions()
                                .captureDraft(f.tenant(), f.boqId(), f.actorId(), policy, now)
                                .flatMap(snapshot -> Uni.createFrom().failure(new IllegalStateException("injected after capture"))))
                        .await().indefinitely());
        assertEquals("injected after capture", failure.getMessage());
        assertEquals(0L, count("SELECT count(*) AS n FROM construction_boq_revision_snapshot WHERE tenant_id=$1 AND boq_id=$2", f.tenant(), f.boqId()));
        assertEquals(0L, count("SELECT count(*) AS n FROM construction_boq_revision_item WHERE tenant_id=$1 AND boq_id=$2", f.tenant(), f.boqId()));
        assertEquals(0L, count("SELECT count(*) AS n FROM construction_boq_revision_counter WHERE tenant_id=$1 AND boq_id=$2", f.tenant(), f.boqId()));
        assertEquals(0L, count("SELECT count(*) AS n FROM construction_outbox_event WHERE tenant_id=$1 AND aggregate_id=$2 AND event_type='BoqRevisionCaptured'", f.tenant(), f.boqId()));
        Row live = pool.preparedQuery("SELECT current_revision, status FROM it_live_boq WHERE tenant_id=$1 AND boq_id=$2")
                .execute(Tuple.of(f.tenant(), f.boqId())).await().indefinitely().iterator().next();
        assertEquals(1, live.getInteger("current_revision"));
        assertEquals("DRAFT", live.getString("status"));
    }

    @Test
    void concurrentApprovalsAllowExactlyOneWinnerAndEmitOneApprovalEvent() throws Exception {
        Fixture f = insertFixture();
        Instant now = Instant.parse("2026-10-13T10:00:00Z");
        var policy = new BoqCalculationPolicy("test-v1", 2, RoundingMode.HALF_UP);
        var captured = runner.inTransaction(context -> context.boqRevisions()
                        .captureDraft(f.tenant(), f.boqId(), f.actorId(), policy, now))
                .await().indefinitely();
        runner.inTransaction(context -> context.boqRevisions()
                        .submitForReview(f.tenant(), f.boqId(), captured.revisionNumber(), f.actorId(), now.plusSeconds(1)))
                .await().indefinitely();

        UUID approverOne = UUID.randomUUID();
        UUID approverTwo = UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> approveAfterBarrier(
                    ready, start, f, captured.revisionNumber(), approverOne, now.plusSeconds(2)));
            Future<Boolean> second = executor.submit(() -> approveAfterBarrier(
                    ready, start, f, captured.revisionNumber(), approverTwo, now.plusSeconds(2)));
            assertTrue(ready.await(10, TimeUnit.SECONDS), "approval workers did not become ready");
            start.countDown();
            boolean firstWon = first.get(20, TimeUnit.SECONDS);
            boolean secondWon = second.get(20, TimeUnit.SECONDS);
            assertNotEquals(firstWon, secondWon, "exactly one approval should succeed");
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        Row live = pool.preparedQuery("SELECT current_revision, status FROM it_live_boq WHERE tenant_id=$1 AND boq_id=$2")
                .execute(Tuple.of(f.tenant(), f.boqId())).await().indefinitely().iterator().next();
        assertEquals(1, live.getInteger("current_revision"));
        assertEquals("APPROVED", live.getString("status"));
        assertEquals(1L, count("SELECT count(*) AS n FROM construction_boq_revision_snapshot WHERE tenant_id=$1 AND boq_id=$2 AND revision_number=1 AND status='APPROVED'", f.tenant(), f.boqId()));
        assertEquals(1L, count("SELECT count(*) AS n FROM construction_outbox_event WHERE tenant_id=$1 AND aggregate_id=$2 AND event_type='BoqRevisionApproved'", f.tenant(), f.boqId()));
    }

    @Test
    void fullLifecycleEmitsOneEventPerTransitionAndRejectsSelfApproval() {
        Fixture f = insertFixture();
        Instant now = Instant.parse("2026-10-13T10:00:00Z");
        var policy = new BoqCalculationPolicy("test-v1", 2, RoundingMode.HALF_UP);

        var captured = runner.inTransaction(context -> context.boqRevisions()
                        .captureDraft(f.tenant(), f.boqId(), f.actorId(), policy, now))
                .await().indefinitely();
        runner.inTransaction(context -> context.boqRevisions()
                        .submitForReview(f.tenant(), f.boqId(), captured.revisionNumber(), f.actorId(), now.plusSeconds(1)))
                .await().indefinitely();

        assertThrows(RuntimeException.class, () -> runner.inTransaction(context -> context.boqRevisions()
                        .approve(f.tenant(), f.boqId(), captured.revisionNumber(), f.actorId(), now.plusSeconds(2)))
                .await().indefinitely(), "the submitter must not approve their own revision");
        assertEquals(0L, count("SELECT count(*) AS n FROM construction_outbox_event WHERE tenant_id=$1 AND aggregate_id=$2 AND event_type='BoqRevisionApproved'", f.tenant(), f.boqId()));

        UUID independentApprover = UUID.randomUUID();
        runner.inTransaction(context -> context.boqRevisions()
                        .approve(f.tenant(), f.boqId(), captured.revisionNumber(), independentApprover, now.plusSeconds(3)))
                .await().indefinitely();

        Row live = pool.preparedQuery("SELECT current_revision, status FROM it_live_boq WHERE tenant_id=$1 AND boq_id=$2")
                .execute(Tuple.of(f.tenant(), f.boqId())).await().indefinitely().iterator().next();
        assertEquals(1, live.getInteger("current_revision"));
        assertEquals("APPROVED", live.getString("status"));
        assertEquals(1L, count("SELECT count(*) AS n FROM construction_outbox_event WHERE tenant_id=$1 AND aggregate_id=$2 AND event_type='BoqRevisionCaptured'", f.tenant(), f.boqId()));
        assertEquals(1L, count("SELECT count(*) AS n FROM construction_outbox_event WHERE tenant_id=$1 AND aggregate_id=$2 AND event_type='BoqRevisionSubmittedForReview'", f.tenant(), f.boqId()));
        assertEquals(1L, count("SELECT count(*) AS n FROM construction_outbox_event WHERE tenant_id=$1 AND aggregate_id=$2 AND event_type='BoqRevisionApproved'", f.tenant(), f.boqId()));
        assertEquals(1L, count("SELECT count(*) AS n FROM construction_boq_revision_snapshot WHERE tenant_id=$1 AND boq_id=$2 AND revision_number=1 AND status='APPROVED'", f.tenant(), f.boqId()));
    }

    @Test
    void staleRevisionSubmissionDoesNotChangeLiveStateOrEmitEvent() {
        Fixture f = insertFixture();
        Instant now = Instant.parse("2026-10-13T10:00:00Z");
        var policy = new BoqCalculationPolicy("test-v1", 2, RoundingMode.HALF_UP);
        runner.inTransaction(context -> context.boqRevisions()
                        .captureDraft(f.tenant(), f.boqId(), f.actorId(), policy, now))
                .await().indefinitely();

        assertThrows(RuntimeException.class, () -> runner.inTransaction(context -> context.boqRevisions()
                        .submitForReview(f.tenant(), f.boqId(), 2, f.actorId(), now.plusSeconds(1)))
                .await().indefinitely());

        Row live = pool.preparedQuery("SELECT current_revision, status FROM it_live_boq WHERE tenant_id=$1 AND boq_id=$2")
                .execute(Tuple.of(f.tenant(), f.boqId())).await().indefinitely().iterator().next();
        assertEquals(1, live.getInteger("current_revision"));
        assertEquals("DRAFT", live.getString("status"));
        assertEquals(0L, count("SELECT count(*) AS n FROM construction_outbox_event WHERE tenant_id=$1 AND aggregate_id=$2 AND event_type='BoqRevisionSubmittedForReview'", f.tenant(), f.boqId()));
        Row snapshot = pool.preparedQuery("SELECT status FROM construction_boq_revision_snapshot WHERE tenant_id=$1 AND boq_id=$2 AND revision_number=1")
                .execute(Tuple.of(f.tenant(), f.boqId())).await().indefinitely().iterator().next();
        assertEquals("DRAFT", snapshot.getString("status"));
    }

    private boolean approveAfterBarrier(CountDownLatch ready, CountDownLatch start, Fixture f,
                                       int revision, UUID actor, Instant now) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("approval start barrier timed out");
        try {
            runner.inTransaction(context -> context.boqRevisions()
                            .approve(f.tenant(), f.boqId(), revision, actor, now))
                    .await().indefinitely();
            return true;
        } catch (RuntimeException expectedConflictOrRace) {
            return false;
        }
    }

    private Fixture insertFixture() {
        String tenant = "factory-it-" + UUID.randomUUID();
        UUID boq = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID item = UUID.randomUUID();
        UUID wbs = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-13T10:00:00Z");
        pool.preparedQuery("INSERT INTO it_live_boq (boq_id, project_id, tenant_id, boq_name, current_revision, status, changed_at) VALUES ($1,$2,$3,$4,1,'DRAFT',$5)")
                .execute(Tuple.of(boq, project, tenant, "Factory integration BOQ", now.atOffset(java.time.ZoneOffset.UTC))).await().indefinitely();
        pool.preparedQuery("INSERT INTO it_live_boq_item (item_id, boq_id, wbs_node_id, item_code, description, item_type, quantity, quantity_unit, rate_amount, rate_unit, currency_code) VALUES ($1,$2,$3,'EX-001','Excavation','WORK',2,'m3',5,'m3','IDR')")
                .execute(Tuple.of(item, boq, wbs)).await().indefinitely();
        return new Fixture(tenant, boq, actor);
    }

    private long count(String sql, String tenant, UUID boqId) {
        Row row = pool.preparedQuery(sql).execute(Tuple.of(tenant, boqId)).await().indefinitely().iterator().next();
        return row.getLong("n");
    }

    private record Fixture(String tenant, UUID boqId, UUID actorId) {}
}
