package tech.kayys.numou.planning.adapter.postgres;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.planning.domain.*;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class JdbcPlanRepositoryIntegrationTest {
    @Inject
    JdbcPlanRepository repository;

    @Inject
    DataSource dataSource;

    @Test
    void persistsWorkingScheduleAndKeepsBaselineImmutableAfterRevision() {
        SchedulePlan plan = SchedulePlan.create("PG-" + UUID.randomUUID(), "Integration plan");
        ScheduleActivity activity = activity("BUILD", 1, 2);
        plan.addActivity(activity);
        repository.save(plan);

        SchedulePlan loaded = repository.findById(plan.id()).orElseThrow();
        ScheduleBaseline baseline = loaded.createBaseline("Approved", Instant.parse("2026-01-01T00:00:00Z"), "test-user");
        repository.save(loaded);

        ScheduleActivity revised = new ScheduleActivity(activity.id(), activity.code(), activity.name(),
                LocalDate.of(2026, 1, 3), LocalDate.of(2026, 1, 4), activity.workItem(), activity.position());
        loaded.replaceActivity(revised);
        repository.save(loaded);

        SchedulePlan reloaded = repository.findById(plan.id()).orElseThrow();
        assertEquals(3, reloaded.revision());
        assertEquals(LocalDate.of(2026, 1, 3), reloaded.activities().get(0).plannedStart());
        assertEquals(LocalDate.of(2026, 1, 1), reloaded.baselines().get(0).activities().get(0).plannedStart());
        assertEquals(baseline.id(), reloaded.baselines().get(0).id());
    }

    @Test
    void rejectsStaleAggregateRevisionInsteadOfOverwritingNewerChanges() {
        SchedulePlan plan = SchedulePlan.create("PG-" + UUID.randomUUID(), "Original");
        plan.addActivity(activity("A", 1, 2));
        repository.save(plan);

        SchedulePlan firstWriter = repository.findById(plan.id()).orElseThrow();
        SchedulePlan staleWriter = repository.findById(plan.id()).orElseThrow();
        firstWriter.rename("First writer");
        repository.save(firstWriter);

        staleWriter.rename("Stale writer");
        assertThrows(JdbcPlanRepository.OptimisticPlanConflictException.class,
                () -> repository.save(staleWriter));
        assertEquals("First writer", repository.findById(plan.id()).orElseThrow().name());
    }

    @Test
    void postgresRejectsDirectBaselineSnapshotDeletion() throws Exception {
        SchedulePlan plan = SchedulePlan.create("PG-" + UUID.randomUUID(), "Immutable evidence");
        plan.addActivity(activity("A", 1, 2));
        repository.save(plan);
        SchedulePlan loaded = repository.findById(plan.id()).orElseThrow();
        loaded.createBaseline("v1", Instant.parse("2026-01-01T00:00:00Z"), "test-user");
        repository.save(loaded);
        UUID baselineId = loaded.baselines().get(0).id();

        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "DELETE FROM schedule_baseline_activity WHERE plan_id = ? AND baseline_id = ?")) {
            statement.setObject(1, plan.id().value());
            statement.setObject(2, baselineId);
            assertThrows(SQLException.class, statement::executeUpdate);
        }
    }

    private static ScheduleActivity activity(String code, int start, int finish) {
        return ScheduleActivity.create(code, code, LocalDate.of(2026, 1, start),
                LocalDate.of(2026, 1, finish), new WorkItemReference("TEST_ITEM", UUID.randomUUID().toString()), 0);
    }
}
