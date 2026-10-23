package tech.kayys.numou.planning.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScheduleChangeExecutionTest {
    private UUID id(long n) { return new UUID(0, n); }

    private final UUID tenant = id(1);
    private final UUID structure = id(2);
    private final UUID requester = id(3);
    private final UUID reviewer = id(4);
    private final PlanId plan = new PlanId(id(5));
    private final Instant requested = Instant.parse("2026-02-01T00:00:00Z");
    private final Instant evidence = Instant.parse("2026-02-02T00:00:00Z");
    private final Instant approvedAt = Instant.parse("2026-02-03T00:00:00Z");
    private final Instant started = Instant.parse("2026-02-04T00:00:00Z");

    private PlanChangeImpactReport report() {
        return new PlanChangeImpactReport(tenant, structure, plan, 4, 5, requested, evidence,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 5), LocalDate.of(2026, 2, 6), 1,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                DependencyScheduleForecastCalculator.METHOD_VERSION);
    }

    private ScheduleChangeRequest approved() {
        return ScheduleChangeRequest.draft(id(10), tenant, structure, plan, 4, 5,
                requester, requested, "Adjust milestone sequence")
                .submit(report())
                .decide(id(11), reviewer, ScheduleChangeDecision.Outcome.APPROVE,
                        approvedAt, "Reviewed", 4);
    }

    private PlanRevisionSnapshot snapshot(long revision, Instant capturedAt, UUID tenantId) {
        return new PlanRevisionSnapshot(tenantId, structure, plan, revision, capturedAt,
                LocalDate.of(2026, 2, 1), Map.of(new ActivityId(id(7)), 2), List.of());
    }

    @Test
    void executionRequiresApprovalAndExactBaseRevision() {
        var draft = ScheduleChangeRequest.draft(id(20), tenant, structure, plan, 4, 5,
                requester, requested, "Change");
        assertThrows(IllegalStateException.class,
                () -> ScheduleChangeExecution.begin(id(30), draft, reviewer, started, 4));
        assertThrows(IllegalStateException.class,
                () -> ScheduleChangeExecution.begin(id(31), approved(), reviewer, started, 5));
    }

    @Test
    void targetRevisionReconcilesToAppliedAndReplayIsIdempotent() {
        var applying = ScheduleChangeExecution.begin(id(40), approved(), reviewer, started, 4);
        var applied = applying.reconcile(snapshot(5, started.plusSeconds(10), tenant), started.plusSeconds(20));
        assertEquals(ScheduleChangeExecution.Status.APPLIED, applied.status());
        assertEquals(5L, applied.resultingRevision());
        assertSame(applied, applied.reconcile(snapshot(5, started.plusSeconds(10), tenant),
                started.plusSeconds(30)));
        assertThrows(IllegalStateException.class,
                () -> applied.fail(started.plusSeconds(40), "late failure"));
    }

    @Test
    void unchangedBaseRemainsInProgressAndForeignScopeIsRejected() {
        var applying = ScheduleChangeExecution.begin(id(50), approved(), reviewer, started, 4);
        assertThrows(IllegalStateException.class,
                () -> applying.reconcile(snapshot(4, started.plusSeconds(5), tenant), started.plusSeconds(10)));
        assertThrows(IllegalArgumentException.class,
                () -> applying.reconcile(snapshot(5, started.plusSeconds(5), id(999)), started.plusSeconds(10)));
    }

    @Test
    void unexpectedRevisionBecomesConflictAndFailureIsAudited() {
        var applying = ScheduleChangeExecution.begin(id(60), approved(), reviewer, started, 4);
        var conflict = applying.reconcile(snapshot(6, started.plusSeconds(5), tenant), started.plusSeconds(10));
        assertEquals(ScheduleChangeExecution.Status.CONFLICT, conflict.status());
        assertTrue(conflict.failureReason().contains("neither the approved base nor target"));
        var failed = applying.fail(started.plusSeconds(20), "Remote write failed");
        assertEquals(ScheduleChangeExecution.Status.FAILED, failed.status());
        assertEquals("Remote write failed", failed.failureReason());
    }
}
