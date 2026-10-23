package tech.kayys.numou.planning.application;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import tech.kayys.numou.planning.application.port.PlanRepository;
import tech.kayys.numou.planning.application.port.ScheduleChangeExecutionStore;
import tech.kayys.numou.planning.domain.ActivityId;
import tech.kayys.numou.planning.domain.PlanChangeImpactReport;
import tech.kayys.numou.planning.domain.PlanId;
import tech.kayys.numou.planning.domain.PlanStatus;
import tech.kayys.numou.planning.domain.ScheduleActivity;
import tech.kayys.numou.planning.domain.ScheduleChangeDecision;
import tech.kayys.numou.planning.domain.ScheduleChangeExecution;
import tech.kayys.numou.planning.domain.ScheduleChangeRequest;
import tech.kayys.numou.planning.domain.SchedulePlan;
import tech.kayys.numou.planning.domain.DependencyScheduleForecastCalculator;

class ApplyApprovedScheduleChangeTest {
    private UUID id(long n) { return new UUID(0, n); }

    private final UUID tenant = id(1);
    private final UUID structure = id(2);
    private final UUID requester = id(3);
    private final UUID reviewer = id(4);
    private final PlanId planId = new PlanId(id(5));
    private final Instant requested = Instant.parse("2026-03-01T00:00:00Z");
    private final Instant evidenceAt = Instant.parse("2026-03-02T00:00:00Z");
    private final Instant approvedAt = Instant.parse("2026-03-03T00:00:00Z");
    private final Clock clock = Clock.fixed(Instant.parse("2026-03-04T00:00:00Z"), ZoneOffset.UTC);

    private ScheduleChangeRequest approvedRequest() {
        var report = new PlanChangeImpactReport(tenant, structure, planId, 4, 5, requested, evidenceAt,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 6), 1,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                DependencyScheduleForecastCalculator.METHOD_VERSION);
        return ScheduleChangeRequest.draft(id(10), tenant, structure, planId, 4, 5,
                requester, requested, "Revise delivery milestone")
                .submit(report)
                .decide(id(11), reviewer, ScheduleChangeDecision.Outcome.APPROVE,
                        approvedAt, "Approved after impact review", 4);
    }

    private SchedulePlan plan(long revision, String name) {
        var activity = new ScheduleActivity(new ActivityId(id(20)), "DEPLOY", name,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 2), null, 0);
        return SchedulePlan.reconstitute(planId, "PLAN-1", "Delivery plan", PlanStatus.DRAFT,
                revision, List.of(activity), List.of(), List.of());
    }

    @Test
    void appliesTargetAndReplayReturnsOriginalReceipt() {
        var plans = new FakePlanRepository(plan(4, "Old deployment"));
        var store = new FakeExecutionStore(plans);
        var useCase = new ApplyApprovedScheduleChange(plans, store, clock);
        var request = approvedRequest();
        var target = plan(5, "New deployment");

        var applied = useCase.execute(id(30), request, reviewer, target);
        assertEquals(ScheduleChangeExecution.Status.APPLIED, applied.status());
        assertEquals(5, plans.findById(planId).orElseThrow().revision());
        assertEquals("New deployment", plans.findById(planId).orElseThrow().activities().get(0).name());

        var replay = useCase.execute(id(30), request, reviewer, target);
        assertSame(applied, replay);
    }

    @Test
    void rejectsWrongPlanOrTargetRevisionBeforeStoreMutation() {
        var plans = new FakePlanRepository(plan(4, "Old deployment"));
        var store = new FakeExecutionStore(plans);
        var useCase = new ApplyApprovedScheduleChange(plans, store, clock);
        var request = approvedRequest();

        assertThrows(IllegalArgumentException.class,
                () -> useCase.execute(id(31), request, reviewer, plan(4, "Wrong revision")));
        var otherPlan = SchedulePlan.reconstitute(new PlanId(id(999)), "OTHER", "Other",
                PlanStatus.DRAFT, 5,
                List.of(new ScheduleActivity(new ActivityId(id(20)), "DEPLOY", "Other",
                        LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 2), null, 0)),
                List.of(), List.of());
        assertThrows(IllegalArgumentException.class,
                () -> useCase.execute(id(32), request, reviewer, otherPlan));
        assertTrue(store.receipts.isEmpty());
    }

    @Test
    void rejectsExecutionIdReuseForDifferentRequest() {
        var plans = new FakePlanRepository(plan(4, "Old deployment"));
        var store = new FakeExecutionStore(plans);
        var useCase = new ApplyApprovedScheduleChange(plans, store, clock);
        var request = approvedRequest();
        var target = plan(5, "New deployment");
        useCase.execute(id(33), request, reviewer, target);

        var other = ScheduleChangeRequest.draft(id(99), tenant, structure, planId, 4, 5,
                requester, requested, "Different request")
                .submit(request.impactReport())
                .decide(id(98), reviewer, ScheduleChangeDecision.Outcome.APPROVE,
                        approvedAt, "Approved", 4);
        assertThrows(IllegalArgumentException.class,
                () -> useCase.execute(id(33), other, reviewer, target));
    }

    private static final class FakePlanRepository implements PlanRepository {
        private final Map<PlanId, SchedulePlan> plans = new HashMap<>();
        FakePlanRepository(SchedulePlan plan) { plans.put(plan.id(), plan); }
        @Override public Optional<SchedulePlan> findById(PlanId id) { return Optional.ofNullable(plans.get(id)); }
        @Override public boolean existsByCode(String code) {
            return plans.values().stream().anyMatch(plan -> plan.code().equalsIgnoreCase(code));
        }
        @Override public void save(SchedulePlan plan) { plans.put(plan.id(), plan); }
    }

    private static final class FakeExecutionStore implements ScheduleChangeExecutionStore {
        private final FakePlanRepository plans;
        private final Map<UUID, ScheduleChangeExecution> receipts = new HashMap<>();
        FakeExecutionStore(FakePlanRepository plans) { this.plans = plans; }

        @Override public Optional<ScheduleChangeExecution> findByExecutionId(UUID executionId) {
            return Optional.ofNullable(receipts.get(executionId));
        }

        @Override public ScheduleChangeExecution applyAtomically(ScheduleChangeRequest request,
                SchedulePlan targetPlan, ScheduleChangeExecution applying) {
            var prior = receipts.get(applying.executionId());
            if (prior != null) return prior;
            var live = plans.findById(request.planId()).orElseThrow();
            if (live.revision() != request.baseRevision()) {
                var conflict = new ScheduleChangeExecution(applying.executionId(), applying.requestId(),
                        applying.tenantId(), applying.structureId(), applying.planId(), applying.baseRevision(),
                        applying.targetRevision(), applying.initiatedBy(), applying.startedAt(),
                        ScheduleChangeExecution.Status.CONFLICT, null, clockTime(), "Base revision changed before atomic apply");
                receipts.put(conflict.executionId(), conflict);
                return conflict;
            }
            plans.save(targetPlan);
            var applied = new ScheduleChangeExecution(applying.executionId(), applying.requestId(),
                    applying.tenantId(), applying.structureId(), applying.planId(), applying.baseRevision(),
                    applying.targetRevision(), applying.initiatedBy(), applying.startedAt(),
                    ScheduleChangeExecution.Status.APPLIED, applying.targetRevision(), clockTime(), null);
            receipts.put(applied.executionId(), applied);
            return applied;
        }

        private Instant clockTime() { return Instant.parse("2026-03-04T00:00:01Z"); }
    }
}
