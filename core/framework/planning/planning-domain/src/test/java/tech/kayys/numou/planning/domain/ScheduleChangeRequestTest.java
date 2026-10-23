package tech.kayys.numou.planning.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScheduleChangeRequestTest {
    private final UUID tenant = id(10), structure = id(20), requester = id(30), reviewer = id(40);
    private final PlanId plan = new PlanId(id(50));
    private final Instant requestedAt = Instant.parse("2026-01-01T00:00:00Z");
    private final Instant evidenceAt = Instant.parse("2026-01-02T00:00:00Z");
    private final Instant decidedAt = Instant.parse("2026-01-03T00:00:00Z");

    private UUID id(long n) { return new UUID(0, n); }
    private PlanChangeImpactReport report(long base, long target) {
        return new PlanChangeImpactReport(tenant, structure, plan, base, target, requestedAt, evidenceAt,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7), 2,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                DependencyScheduleForecastCalculator.METHOD_VERSION);
    }
    private ScheduleChangeRequest draft() {
        return ScheduleChangeRequest.draft(id(100), tenant, structure, plan, 4, 5,
                requester, requestedAt, "Change delivery sequence");
    }

    @Test
    void submissionAndApprovalPreserveEvidenceAndAudit() {
        var submitted = draft().submit(report(4, 5));
        assertEquals(ScheduleChangeRequest.Status.SUBMITTED, submitted.status());
        var approved = submitted.decide(id(200), reviewer, ScheduleChangeDecision.Outcome.APPROVE,
                decidedAt, "Impact reviewed and accepted", 4);
        assertEquals(ScheduleChangeRequest.Status.APPROVED, approved.status());
        assertSame(submitted.impactReport(), approved.impactReport());
        assertEquals(1, approved.decisions().size());
        assertEquals(reviewer, approved.decisions().get(0).actorId());
        assertEquals(ScheduleChangeDecision.Outcome.APPROVE, approved.decisions().get(0).outcome());
    }

    @Test
    void rejectsSelfApprovalAndStaleEvidence() {
        var submitted = draft().submit(report(4, 5));
        assertThrows(IllegalArgumentException.class, () -> submitted.decide(id(201), requester,
                ScheduleChangeDecision.Outcome.APPROVE, decidedAt, "Self approval", 4));
        assertThrows(IllegalStateException.class, () -> submitted.decide(id(202), reviewer,
                ScheduleChangeDecision.Outcome.APPROVE, decidedAt, "Outdated evidence", 5));
        var stale = submitted.decide(id(203), reviewer, ScheduleChangeDecision.Outcome.MARK_STALE,
                decidedAt, "Plan revision advanced", 5);
        assertEquals(ScheduleChangeRequest.Status.STALE, stale.status());
        assertEquals(ScheduleChangeDecision.Outcome.MARK_STALE, stale.decisions().get(0).outcome());
    }

    @Test
    void repeatedExactDecisionIsIdempotentButDecisionIdCannotBeReused() {
        var submitted = draft().submit(report(4, 5));
        var approved = submitted.decide(id(210), reviewer, ScheduleChangeDecision.Outcome.APPROVE,
                decidedAt, "Reviewed", 4);
        var replay = approved.decide(id(210), reviewer, ScheduleChangeDecision.Outcome.APPROVE,
                decidedAt, "Reviewed", 4);
        assertSame(approved, replay);
        assertThrows(IllegalStateException.class, () -> approved.decide(id(211), reviewer,
                ScheduleChangeDecision.Outcome.REJECT, decidedAt, "Changed mind", 4));
        assertThrows(IllegalStateException.class, () -> approved.decide(id(210), reviewer,
                ScheduleChangeDecision.Outcome.APPROVE, decidedAt, "Different payload", 4));
    }

    @Test
    void validatesScopeAndRevisionProvenanceOnSubmission() {
        var request = draft();
        assertThrows(IllegalArgumentException.class, () -> request.submit(report(3, 5)));
        var foreign = new PlanChangeImpactReport(id(999), structure, plan, 4, 5, requestedAt, evidenceAt,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7), 2,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                DependencyScheduleForecastCalculator.METHOD_VERSION);
        assertThrows(IllegalArgumentException.class, () -> request.submit(foreign));
    }

    @Test
    void supersedingStaleRequestAddsAuditEntry() {
        var submitted = draft().submit(report(4, 5));
        var stale = submitted.decide(id(220), reviewer, ScheduleChangeDecision.Outcome.MARK_STALE,
                decidedAt, "Base revision no longer current", 6);
        var superseded = stale.supersede(id(221), requester, decidedAt.plusSeconds(60),
                "Replaced by a new proposal", 6);
        assertEquals(ScheduleChangeRequest.Status.SUPERSEDED, superseded.status());
        assertEquals(2, superseded.decisions().size());
        assertEquals(ScheduleChangeDecision.Outcome.SUPERSEDE, superseded.decisions().get(1).outcome());
    }
}
