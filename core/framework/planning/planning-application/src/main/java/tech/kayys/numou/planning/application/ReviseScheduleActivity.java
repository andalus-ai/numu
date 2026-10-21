package tech.kayys.numou.planning.application;

import tech.kayys.numou.planning.application.port.PlanRepository;
import tech.kayys.numou.planning.domain.ActivityId;
import tech.kayys.numou.planning.domain.PlanId;
import tech.kayys.numou.planning.domain.ScheduleActivity;

import java.util.Objects;

/** Changes the working forecast only; already-created baselines remain untouched. */
public final class ReviseScheduleActivity {
    private final PlanRepository repository;

    public ReviseScheduleActivity(PlanRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Plan repository cannot be null");
    }

    public void execute(PlanId planId, ActivityId activityId, ScheduleActivity replacement) {
        if (!Objects.requireNonNull(activityId, "Activity id cannot be null").equals(
                Objects.requireNonNull(replacement, "Replacement activity cannot be null").id())) {
            throw new IllegalArgumentException("Replacement must preserve the activity identity");
        }
        var plan = repository.findById(Objects.requireNonNull(planId, "Plan id cannot be null"))
                .orElseThrow(() -> new IllegalArgumentException("Schedule plan was not found"));
        plan.replaceActivity(replacement);
        repository.save(plan);
    }
}
