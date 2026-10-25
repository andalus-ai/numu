package tech.kayys.numou.planning.adapter.postgres;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.planning.domain.*;
import tech.kayys.numou.planning.application.RegisterSchedulePlanScope;

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
    @Inject RegisterSchedulePlanScope scopeRegistration;
    @Inject JdbcScheduleChangeOutboxRelayStore outboxRelayStore;

    @Test
    void appliesPlanAndReceiptTogetherAndReturnsSameReceiptOnReplay() throws Exception {
        Fixture f = fixture();
        ScheduleChangeExecution applying = ScheduleChangeExecution.begin(
                f.executionId, f.request, f.initiator, f.startedAt, f.base.revision());

        ScheduleChangeExecution applied = executions.applyAtomically(f.request, f.target, applying);
        assertEquals(ScheduleChangeExecution.Status.APPLIED, applied.status());
        assertEquals(2, plans.findById(f.base.id()).orElseThrow().revision());
        assertReceiptEquivalent(applied, executions.findByExecutionId(f.executionId).orElseThrow());
        assertEquals(1, outboxCount(f.executionId, "ScheduleChangeApplied"));

        ScheduleChangeExecution replay = executions.applyAtomically(f.request, f.target, applying);
        assertReceiptEquivalent(applied, replay);
        assertEquals(2, plans.findById(f.base.id()).orElseThrow().revision());
        assertEquals(1, outboxCount(f.executionId, "ScheduleChangeApplied"));
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
        assertReceiptEquivalent(conflict, executions.findByExecutionId(f.executionId).orElseThrow());
        assertEquals(1, outboxCount(f.executionId, "ScheduleChangeConflicted"));
        assertEquals(0, outboxCount(f.executionId, "ScheduleChangeApplied"));
    }



    @Test
    void scopeRegistrationIsIdempotentButCannotReassignOwnership() throws Exception {
        Fixture f = fixture();
        assertDoesNotThrow(() -> scopeRegistration.execute(f.base.id(), f.tenant, f.structure));
        assertThrows(IllegalStateException.class, () -> scopeRegistration.execute(
                f.base.id(), UUID.randomUUID(), f.structure));
        assertThrows(IllegalStateException.class, () -> scopeRegistration.execute(
                f.base.id(), f.tenant, UUID.randomUUID()));
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
            assertReceiptEquivalent(a, executions.findByExecutionId(firstId).orElseThrow());
            assertReceiptEquivalent(b, executions.findByExecutionId(secondId).orElseThrow());
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
        assertEquals(0, outboxCount(f.executionId, null));
    }


    @Test
    void outboxClaimsAreLeasedAndAcknowledgementsAreFencedByClaimToken() throws Exception {
        Fixture f = fixture();
        ScheduleChangeExecution applying = ScheduleChangeExecution.begin(
                f.executionId, f.request, f.initiator, f.startedAt, f.base.revision());
        ScheduleChangeExecution applied = executions.applyAtomically(f.request, f.target, applying);
        assertEquals(ScheduleChangeExecution.Status.APPLIED, applied.status());

        Instant now = Instant.now();
        UUID token = UUID.randomUUID();
        var claimed = outboxRelayStore.claimDue(1000, now, now.plusSeconds(30), token);
        var event = claimed.stream().filter(e -> e.executionId().equals(f.executionId)).findFirst().orElseThrow();
        assertEquals("ScheduleChangeApplied", event.eventType());
        assertTrue(event.payload().contains(f.executionId.toString()));
        assertEquals(token, event.claimToken());

        assertFalse(outboxRelayStore.markPublished(event.outboxId(), UUID.randomUUID(), now));
        assertTrue(outboxRelayStore.markPublished(event.outboxId(), token, now));
        assertFalse(outboxRelayStore.markPublished(event.outboxId(), token, now.plusSeconds(1)));
        assertTrue(outboxRelayStore.claimDue(1000, now.plusSeconds(1), now.plusSeconds(31), UUID.randomUUID())
                .stream().noneMatch(e -> e.outboxId().equals(event.outboxId())));
    }

    @Test
    void failedOutboxDeliveryCanBeRetriedAndExpiredLeaseCanBeReclaimed() throws Exception {
        Fixture f = fixture();
        ScheduleChangeExecution applying = ScheduleChangeExecution.begin(
                f.executionId, f.request, f.initiator, f.startedAt, f.base.revision());
        executions.applyAtomically(f.request, f.target, applying);

        Instant now = Instant.now();
        UUID firstToken = UUID.randomUUID();
        var first = outboxRelayStore.claimDue(1000, now, now.plusSeconds(5), firstToken).stream()
                .filter(e -> e.executionId().equals(f.executionId)).findFirst().orElseThrow();
        assertTrue(outboxRelayStore.recordFailure(first.outboxId(), firstToken,
                now.plusSeconds(60), "broker unavailable"));
        assertTrue(outboxRelayStore.claimDue(1000, now.plusSeconds(30), now.plusSeconds(35), UUID.randomUUID())
                .stream().noneMatch(e -> e.outboxId().equals(first.outboxId())));

        UUID secondToken = UUID.randomUUID();
        var second = outboxRelayStore.claimDue(1000, now.plusSeconds(61), now.plusSeconds(90), secondToken)
                .stream().filter(e -> e.outboxId().equals(first.outboxId())).findFirst().orElseThrow();
        assertNotEquals(firstToken, second.claimToken());
        assertFalse(outboxRelayStore.markPublished(second.outboxId(), firstToken, now.plusSeconds(62)));
        assertTrue(outboxRelayStore.markPublished(second.outboxId(), secondToken, now.plusSeconds(62)));
    }


    @Test
    void expiredLeaseCannotBeAcknowledgedAndCanBeReclaimed() throws Exception {
        Fixture f = fixture();
        ScheduleChangeExecution applying = ScheduleChangeExecution.begin(
                f.executionId, f.request, f.initiator, f.startedAt, f.base.revision());
        executions.applyAtomically(f.request, f.target, applying);

        Instant now = Instant.now();
        UUID oldToken = UUID.randomUUID();
        var first = outboxRelayStore.claimDue(1000, now, now.plusSeconds(1), oldToken).stream()
                .filter(e -> e.executionId().equals(f.executionId)).findFirst().orElseThrow();
        assertFalse(outboxRelayStore.markPublished(first.outboxId(), oldToken, now.plusSeconds(2)));

        UUID newToken = UUID.randomUUID();
        var reclaimed = outboxRelayStore.claimDue(1000, now.plusSeconds(2), now.plusSeconds(30), newToken)
                .stream().filter(e -> e.outboxId().equals(first.outboxId())).findFirst().orElseThrow();
        assertEquals(newToken, reclaimed.claimToken());
        assertFalse(outboxRelayStore.markPublished(first.outboxId(), oldToken, now.plusSeconds(3)));
        assertTrue(outboxRelayStore.markPublished(first.outboxId(), newToken, now.plusSeconds(3)));
    }

    @Test
    void exhaustedDeliveryMovesToDeadLetterAndAppearsInHealthSnapshot() throws Exception {
        Fixture f = fixture();
        ScheduleChangeExecution applying = ScheduleChangeExecution.begin(
                f.executionId, f.request, f.initiator, f.startedAt, f.base.revision());
        executions.applyAtomically(f.request, f.target, applying);

        Instant now = Instant.now();
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "UPDATE schedule_change_outbox SET attempt_count = 9, available_at = ? WHERE execution_id = ?")) {
            statement.setTimestamp(1, java.sql.Timestamp.from(now.minusSeconds(5)));
            statement.setObject(2, f.executionId);
            assertEquals(1, statement.executeUpdate());
        }

        UUID token = UUID.randomUUID();
        var event = outboxRelayStore.claimDue(1000, now, now.plusSeconds(30), token).stream()
                .filter(e -> e.executionId().equals(f.executionId)).findFirst().orElseThrow();
        assertEquals(10, event.attemptCount());
        assertTrue(outboxRelayStore.recordFailure(event.outboxId(), token,
                now.plusSeconds(30), "simulated broker outage"));

        assertTrue(outboxRelayStore.claimDue(1000, now.plusSeconds(31), now.plusSeconds(61),
                UUID.randomUUID()).stream().noneMatch(e -> e.executionId().equals(f.executionId())));
        var health = outboxRelayStore.healthSnapshot();
        assertTrue(health.deadLetterCount() >= 1);
        assertTrue(health.pendingCount() >= 0);
        assertTrue(health.leasedCount() >= 0);
        assertTrue(health.oldestPendingAgeSeconds() >= 0);
    }

    @Test
    void concurrentRelayWorkersDoNotClaimTheSameEvent() throws Exception {
        Fixture f = fixture();
        ScheduleChangeExecution applying = ScheduleChangeExecution.begin(
                f.executionId, f.request, f.initiator, f.startedAt, f.base.revision());
        executions.applyAtomically(f.request, f.target, applying);

        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var first = pool.submit(() -> claimAfterBarrier(ready, start, UUID.randomUUID()));
            var second = pool.submit(() -> claimAfterBarrier(ready, start, UUID.randomUUID()));
            assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();

            var a = first.get(20, java.util.concurrent.TimeUnit.SECONDS).stream()
                    .filter(event -> event.executionId().equals(f.executionId)).toList();
            var b = second.get(20, java.util.concurrent.TimeUnit.SECONDS).stream()
                    .filter(event -> event.executionId().equals(f.executionId)).toList();
            assertEquals(1, a.size() + b.size(),
                    "a single due outbox event must be claimed by only one concurrent worker");
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    private List<JdbcScheduleChangeOutboxRelayStore.ClaimedEvent> claimAfterBarrier(
            java.util.concurrent.CountDownLatch ready,
            java.util.concurrent.CountDownLatch start,
            UUID token) throws Exception {
        ready.countDown();
        if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for concurrent relay start");
        }
        Instant now = Instant.now();
        return outboxRelayStore.claimDue(1000, now, now.plusSeconds(30), token);
    }

    @Test
    void deadLetterReplayIsAuditedAndCannotBeRequestedTwice() throws Exception {
        Fixture f = fixture();
        ScheduleChangeExecution applying = ScheduleChangeExecution.begin(
                f.executionId, f.request, f.initiator, f.startedAt, f.base.revision());
        executions.applyAtomically(f.request, f.target, applying);

        Instant exhaustedAt = Instant.now();
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     UPDATE schedule_change_outbox
                     SET attempt_count = 10, dead_lettered_at = ?, claim_token = NULL, claimed_until = NULL
                     WHERE execution_id = ?
                     """)) {
            statement.setTimestamp(1, java.sql.Timestamp.from(exhaustedAt));
            statement.setObject(2, f.executionId);
            assertEquals(1, statement.executeUpdate());
        }

        UUID outboxId;
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT outbox_id FROM schedule_change_outbox WHERE execution_id = ?")) {
            statement.setObject(1, f.executionId);
            try (var rs = statement.executeQuery()) {
                assertTrue(rs.next());
                outboxId = (UUID) rs.getObject(1);
            }
        }

        Instant replayAt = Instant.now().plusSeconds(1);
        assertTrue(outboxRelayStore.replayDeadLetter(outboxId, "operator@example.test",
                "Broker credentials repaired; approved replay", replayAt));
        assertFalse(outboxRelayStore.replayDeadLetter(outboxId, "operator@example.test",
                "Duplicate replay must be rejected", replayAt.plusSeconds(1)));

        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     SELECT attempt_count, dead_lettered_at, last_error
                     FROM schedule_change_outbox WHERE outbox_id = ?
                     """)) {
            statement.setObject(1, outboxId);
            try (var rs = statement.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt("attempt_count"));
                assertNull(rs.getTimestamp("dead_lettered_at"));
                assertNull(rs.getString("last_error"));
            }
        }

        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     SELECT count(*) FROM schedule_change_outbox_replay_audit
                     WHERE outbox_id = ? AND replayed_by = ? AND reason = ?
                     """)) {
            statement.setObject(1, outboxId);
            statement.setString(2, "operator@example.test");
            statement.setString(3, "Broker credentials repaired; approved replay");
            try (var rs = statement.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
        }

        var replayed = outboxRelayStore.claimDue(1000, replayAt.plusSeconds(1),
                replayAt.plusSeconds(31), UUID.randomUUID());
        assertTrue(replayed.stream().anyMatch(event -> event.outboxId().equals(outboxId)
                && event.attemptCount() == 1));
    }

    @Test
    void deadLetterReplayRejectsInvalidOperatorOrReason() {
        assertThrows(IllegalArgumentException.class, () -> outboxRelayStore.replayDeadLetter(
                UUID.randomUUID(), " ", "valid reason", Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> outboxRelayStore.replayDeadLetter(
                UUID.randomUUID(), "operator", "  ", Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> outboxRelayStore.replayDeadLetter(
                UUID.randomUUID(), "operator", "r".repeat(501), Instant.now()));
    }

    private void assertReceiptEquivalent(ScheduleChangeExecution expected, ScheduleChangeExecution actual) {
        assertEquals(expected.executionId(), actual.executionId());
        assertEquals(expected.requestId(), actual.requestId());
        assertEquals(expected.tenantId(), actual.tenantId());
        assertEquals(expected.structureId(), actual.structureId());
        assertEquals(expected.planId(), actual.planId());
        assertEquals(expected.baseRevision(), actual.baseRevision());
        assertEquals(expected.targetRevision(), actual.targetRevision());
        assertEquals(expected.initiatedBy(), actual.initiatedBy());
        assertEquals(expected.startedAt(), actual.startedAt());
        assertEquals(expected.status(), actual.status());
        assertEquals(expected.resultingRevision(), actual.resultingRevision());
        assertEquals(expected.completedAt() == null ? null : expected.completedAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS),
                actual.completedAt() == null ? null : actual.completedAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        assertEquals(expected.failureReason(), actual.failureReason());
    }

    private int outboxCount(UUID executionId, String eventType) throws Exception {
        String sql = eventType == null
                ? "SELECT count(*) FROM schedule_change_outbox WHERE execution_id=?"
                : "SELECT count(*) FROM schedule_change_outbox WHERE execution_id=? AND event_type=?";
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, executionId);
            if (eventType != null) statement.setString(2, eventType);
            try (var rs = statement.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
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

        scopeRegistration.execute(base.id(), tenant, structure);

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
