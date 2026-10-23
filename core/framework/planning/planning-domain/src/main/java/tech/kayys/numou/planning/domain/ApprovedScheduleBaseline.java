package tech.kayys.numou.planning.domain;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
/** Frozen schedule dates approved for comparison with later forecasts. */
public record ApprovedScheduleBaseline(UUID baselineId, UUID tenantId, UUID structureId, PlanId planId,
        long planRevision, Instant approvedAt, LocalDate projectStart, LocalDate projectFinish,
        Map<ActivityId, ActivityDates> activities) {
    public ApprovedScheduleBaseline {
        Objects.requireNonNull(baselineId); Objects.requireNonNull(tenantId); Objects.requireNonNull(structureId);
        Objects.requireNonNull(planId); Objects.requireNonNull(approvedAt); Objects.requireNonNull(projectStart);
        Objects.requireNonNull(projectFinish);
        activities = Map.copyOf(Objects.requireNonNull(activities));
        if (planRevision < 0) throw new IllegalArgumentException("Plan revision cannot be negative");
        if (projectFinish.isBefore(projectStart)) throw new IllegalArgumentException("Project finish cannot precede start");
        if (activities.isEmpty()) throw new IllegalArgumentException("Baseline must contain at least one activity");
        activities.forEach((id, dates) -> { Objects.requireNonNull(id); Objects.requireNonNull(dates); });
    }
    public record ActivityDates(LocalDate start, LocalDate finish) {
        public ActivityDates {
            Objects.requireNonNull(start); Objects.requireNonNull(finish);
            if (finish.isBefore(start)) throw new IllegalArgumentException("Activity finish cannot precede start");
        }
    }
}
