package tech.kayys.numou.planning.adapter.postgres;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.planning.domain.*;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class JdbcScheduleChangeExecutionStoreIntegrationTest {
    @Inject JdbcPlanRepository plans;
    @Inject JdbcScheduleChangeExecutionStore executions;
    @Inject DataSource dataSource;

    @Test
    void appliesPlanAndReceiptTogetherAndReturnsSameReceiptOnReplay() throws Exception {
        Fixture f = fixture();
        ScheduleChangeExecution applying = ScheduleChangeExecution.begin(
                f.executionId, f.request, f.initiator, f.startedAt, f.base.revision());

        ScheduleChangeExecution applied = executions.applyAtomically(f.request, f.target, applying);
        assertEquals(ScheduleChangeExecution.Status.APPLIED, applied.status());
        assertEquals(2, plans.findById(f.base.id()).orElseThrow().revision());
        assertEquals(applied, executions.findByExecutionId(f.executionId).orElseThrow());

        ScheduleChangeExecution replay = executions.applyAtomically(f.request, f.target, applying);
        assertEquals(applied, replay);
        assertEquals(2, plans.findById(f.base.id()).orElseThrow().revision());
    }

    @Test
    void recordsConflictWithoutMutatingPlanWhenBaseRevisionIsStale() throws Exception {
        Fixture f = fixture();
        SchedulePlan interveningWriter = plans.findById(f.base.id()).orElseThrow();
        interveningWriter.rename("Concurrent writer");
        plans.save(interveningWriter);

        ScheduleChangeExecution applying = new ScheduleChangeExecution(f.executionId, f.request.requestId(),
                f.tenant, f.structure, f.base.id(), f.request.baseRevision(), f.request.targetRevision(),
                f.initiator, f.startedAt, ScheduleChangeExecution.Status.APPLYING, null, null, null);
        ScheduleChangeExecution conflict = executions.applyAtomically(f.request, f.target, applying);

        assertEquals(ScheduleChangeExecution.Status.CONFLICT, conflict.status());
        assertEquals("Concurrent writer", plans.findById(f.base.id()).orElseThrow().name());
        assertEquals(conflict, executions.findByExecutionId(f.executionId).orElseThrow());
    }


    @Test
    void concurrentExecutionsFromSameBaseProduceOneApplyAndOneConflict() throws Exception {
        Fixture f = fixture();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var first = pool.submit(() -> executeAfterBarrier(f, firstId, ready, start));
            var second = pool.submit(() -> executeAfterBarrier(f, secondId, ready, start));
            assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();

            ScheduleChangeExecution a = first.get(20, java.util.concurrent.TimeUnit.SECONDS);
            ScheduleChangeExecution b = second.get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(1, java.util.stream.Stream.of(a, b)
                    .filter(r -> r.status() == ScheduleChangeExecution.Status.APPLIED).count());
            assertEquals(1, java.util.stream.Stream.of(a, b)
                    .filter(r -> r.status() == ScheduleChangeExecution.Status.CONFLICT).count());
            assertEquals(2, plans.findById(f.base.id()).orElseThrow().revision());
            assertEquals(a, executions.findByExecutionId(firstId).orElseGet(
                    () -> executions.findByExecutionId(secondId).orElseThrow()));
            assertTrue(executions.findByExecutionId(firstId).isPresent());
            assertTrue(executions.findByExecutionId(secondId).isPresent());
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void failedScheduleReplacementRollsBackPlanAndDoesNotLeaveReceipt() throws Exception {
        Fixture f = fixture();
        ScheduleActivity original = f.base.activities().get(0);
        try (var connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO schedule_activity_progress_observation(plan_id, observation_id, activity_id, "
                             + "as_of_date, percent_complete, recorded_at, recorded_by, idempotency_key) "
                             + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setObject(1, f.base.id().value());
            statement.setObject(2, UUID.randomUUID());
            statement.setObject(3, original.id().value());
            statement.setObject(4, LocalDate.of(2026, 10, 22));
            statement.setBigDecimal(5, new java.math.BigDecimal("10.00"));
            statement.setTimestamp(6, java.sql.Timestamp.from(f.startedAt));
            statement.setString(7, f.initiator.toString());
            statement.setString(8, "m28-" + UUID.randomUUID());
            statement.executeUpdate();
        }

        SchedulePlan invalidTarget = plans.findById(f.base.id()).orElseThrow();
        invalidTarget.removeActivity(original.id());
        invalidTarget.markPersisted(2);
        ScheduleChangeExecution applying = ScheduleChangeExecution.begin(
                f.executionId, f.request, f.initiator, f.startedAt, f.base.revision());

        assertThrows(IllegalStateException.class,
                () -> executions.applyAtomically(f.request, invalidTarget, applying));
        SchedulePlan unchanged = plans.findById(f.base.id()).orElseThrow();
        assertEquals(1, unchanged.revision());
        assertEquals(1, unchanged.activities().size());
        assertTrue(executions.findByExecutionId(f.executionId).isEmpty());
    }

    private ScheduleChangeExecution executeAfterBarrier(Fixture f, UUID executionId,
            java.util.concurrent.CountDownLatch ready, java.util.concurrent.CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS))
            throw new IllegalStateException("Timed out waiting for concurrent execution start");
        ScheduleChangeExecution applying = ScheduleChangeExecution.begin(
                executionId, f.request, f.initiator, f.startedAt, f.base.revision());
        return executions.applyAtomically(f.request, f.target, applying);
    }

    private Fixture fixture() throws Exception {
        UUID tenant = UUID.randomUUID(), structure = UUID.randomUUID();
        UUID requester = UUID.randomUUID(), approver = UUID.randomUUID(), initiator = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        Instant requestedAt = Instant.parse("2026-10-20T10:00:00Z");
        Instant capturedAt = requestedAt.plusSeconds(60);
        Instant approvedAt = capturedAt.plusSeconds(60);
        Instant startedAt = approvedAt.plusSeconds(60);

        SchedulePlan base = SchedulePlan.create("M27-" + UUID.randomUUID(), "Plan before change");
        ScheduleActivity activity = ScheduleActivity.create("A", "Activity A",
                LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 22), null, 0);
        base.addActivity(activity);
        plans.save(base);

        try (var connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO schedule_plan_scope(plan_id, tenant_id, structure_id) VALUES (?,?,?)")) {
            statement.setObject(1, base.id().value());
            statement.setObject(2, tenant);
            statement.setObject(3, structure);
            statement.executeUpdate();
        }

        PlanChangeImpactReport impact = new PlanChangeImpactReport(tenant, structure, base.id(),
                1, 2, requestedAt, capturedAt, LocalDate.of(2026, 10, 21),
                LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 23), 1,
                List.of(), List.of(), List.of(), List.of(), List.of(), "m27-test");
        ScheduleChangeRequest request = ScheduleChangeRequest.draft(UUID.randomUUID(), tenant, structure,
                base.id(), 1, 2, requester, requestedAt, "Revise activity dates").submit(impact)
                .decide(UUID.randomUUID(), approver, ScheduleChangeDecision.Outcome.APPROVE,
                        approvedAt, "Approved for execution", 1);

        SchedulePlan target = plans.findById(base.id()).orElseThrow();
        target.replaceActivity(new ScheduleActivity(activity.id(), activity.code(), activity.name(),
                LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 23), null, 0));
        target.markPersisted(2);
        return new Fixture(tenant, structure, initiator, executionId, startedAt, base, target, request);
    }

    private record Fixture(UUID tenant, UUID structure, UUID initiator, UUID executionId, Instant startedAt,
                           SchedulePlan base, SchedulePlan target, ScheduleChangeRequest request) {}
}
