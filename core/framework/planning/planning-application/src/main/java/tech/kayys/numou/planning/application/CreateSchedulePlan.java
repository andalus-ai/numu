package tech.kayys.numou.planning.application;

import tech.kayys.numou.planning.application.port.PlanRepository;
import tech.kayys.numou.planning.domain.SchedulePlan;

import java.util.Objects;

/** Creates a uniquely coded, industry-neutral schedule plan. */
public final class CreateSchedulePlan {
    private final PlanRepository repository;

    public CreateSchedulePlan(PlanRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Plan repository cannot be null");
    }

    public SchedulePlan execute(String code, String name) {
        Objects.requireNonNull(code, "Plan code cannot be null");
        if (repository.existsByCode(code.trim())) {
            throw new IllegalArgumentException("A schedule plan with this code already exists");
        }
        SchedulePlan plan = SchedulePlan.create(code, name);
        repository.save(plan);
        return plan;
    }
}
