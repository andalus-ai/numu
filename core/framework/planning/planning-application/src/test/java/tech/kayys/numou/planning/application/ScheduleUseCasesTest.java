package tech.kayys.numou.planning.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.planning.application.port.PlanRepository;
import tech.kayys.numou.planning.domain.PlanId;
import tech.kayys.numou.planning.domain.SchedulePlan;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ScheduleUseCasesTest {
    @Test
    void createsPlansAndRejectsDuplicateCodes() {
        var repository = new InMemoryRepository();
        var useCase = new CreateSchedulePlan(repository);
        var plan = useCase.execute("APP-1", "Application rollout");
        assertEquals("APP-1", plan.code());
        assertThrows(IllegalArgumentException.class, () -> useCase.execute("APP-1", "Duplicate"));
    }

    @Test
    void reviseAndBaselineUseCasesPreserveApprovedSnapshot() {
        var repository = new InMemoryRepository();
        var plan = SchedulePlan.create("APP-2", "Rollout");
        var activity = tech.kayys.numou.planning.domain.ScheduleActivity.create("DEPLOY", "Deploy",
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 2),
                new tech.kayys.numou.planning.domain.WorkItemReference("BACKLOG", "story-9"), 0);
        plan.addActivity(activity);
        repository.save(plan);

        var clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        var baseline = new CreateScheduleBaseline(repository, clock).execute(plan.id(), "Approved", "pm");
        var replacement = new tech.kayys.numou.planning.domain.ScheduleActivity(activity.id(), activity.code(),
                activity.name(), LocalDate.of(2026, 2, 3), LocalDate.of(2026, 2, 4),
                activity.workItem(), activity.position());
        new ReviseScheduleActivity(repository).execute(plan.id(), activity.id(), replacement);

        assertEquals(LocalDate.of(2026, 2, 1), baseline.activities().get(0).plannedStart());
        assertEquals(LocalDate.of(2026, 2, 3), repository.findById(plan.id()).orElseThrow()
                .activities().get(0).plannedStart());
    }

    private static final class InMemoryRepository implements PlanRepository {
        private final Map<PlanId, SchedulePlan> plans = new HashMap<>();
        @Override public Optional<SchedulePlan> findById(PlanId id) { return Optional.ofNullable(plans.get(id)); }
        @Override public boolean existsByCode(String code) {
            return plans.values().stream().anyMatch(plan -> plan.code().equalsIgnoreCase(code));
        }
        @Override public void save(SchedulePlan plan) { plans.put(plan.id(), plan); }
    }
}
