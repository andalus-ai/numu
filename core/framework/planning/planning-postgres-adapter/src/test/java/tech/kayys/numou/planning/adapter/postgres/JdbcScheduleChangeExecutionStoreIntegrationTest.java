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
