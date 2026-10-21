package tech.kayys.numou.planning.adapter.postgres;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.planning.application.port.PlanRepository;
import tech.kayys.numou.planning.domain.*;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class JdbcActivityProgressRepositoryIntegrationTest {
    @Inject DataSource dataSource;
    @Inject PlanRepository planRepository;
    @Inject JdbcActivityProgressRepository progressRepository;

    @Test
    void persistsHistoryRejectsConflictingReplayAndKeepsEvidenceWhenScheduleIsRevised() throws Exception {
        SchedulePlan plan = SchedulePlan.create("PROGRESS-" + UUID.randomUUID(), "Progress test");
        ScheduleActivity activity = ScheduleActivity.create("A-1", "Deliver work",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 10),
                new WorkItemReference("SCOPE_NODE", "scope-1"), 0);
        plan.addActivity(activity);
        planRepository.save(plan);

        ActivityProgressObservation first = observation(activity.id(), "daily-1", "25");
        assertTrue(progressRepository.append(plan.id(), first));
        assertFalse(progressRepository.append(plan.id(), observation(activity.id(), "daily-1", "25.00")));
        assertEquals(1, progressRepository.findHistory(plan.id(), activity.id()).size());
        assertEquals(new BigDecimal("25.00"), progressRepository.findLatestAsOf(
                plan.id(), activity.id(), LocalDate.of(2026, 5, 2)).orElseThrow().percentComplete());

        assertThrows(IllegalArgumentException.class,
                () -> progressRepository.append(plan.id(), observation(activity.id(), "daily-1", "30")));

        plan.rename("Revised plan");
        planRepository.save(plan);
        assertEquals(1, progressRepository.findHistory(plan.id(), activity.id()).size());
        assertThrows(Exception.class, () -> deleteObservation(plan.id(), first.id()));
    }

    @Test
    void cannotDeleteAnActivityWithRecordedProgress() {
        SchedulePlan plan = SchedulePlan.create("PROGRESS-DELETE-" + UUID.randomUUID(), "Progress delete test");
        ScheduleActivity activity = ScheduleActivity.create("A-1", "Deliver work",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 10),
                new WorkItemReference("SCOPE_NODE", "scope-1"), 0);
        plan.addActivity(activity);
        planRepository.save(plan);
        progressRepository.append(plan.id(), observation(activity.id(), "daily-2", "10"));
        plan.removeActivity(activity.id());
        assertThrows(RuntimeException.class, () -> planRepository.save(plan));
        assertEquals(1, progressRepository.findHistory(plan.id(), activity.id()).size());
    }

    private ActivityProgressObservation observation(ActivityId activityId, String key, String percent) {
        return ActivityProgressObservation.record(activityId, LocalDate.of(2026, 5, 2),
                new BigDecimal(percent), Instant.parse("2026-05-02T12:00:00Z"), "project-lead", key, "daily-report");
    }

    private void deleteObservation(PlanId planId, UUID observationId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM schedule_activity_progress_observation WHERE plan_id = ? AND observation_id = ?")) {
            statement.setObject(1, planId.value());
            statement.setObject(2, observationId);
            statement.executeUpdate();
        }
    }
}
