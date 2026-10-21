package tech.kayys.numou.planning.application;

import tech.kayys.numou.planning.application.port.PlanRepository;
import tech.kayys.numou.planning.domain.PlanId;
import tech.kayys.numou.planning.domain.ScheduleBaseline;

import java.time.Clock;
import java.util.Objects;

/** Captures an immutable baseline using an injected clock for deterministic testing. */
public final class CreateScheduleBaseline {
    private final PlanRepository repository;
    private final Clock clock;

    public CreateScheduleBaseline(PlanRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "Plan repository cannot be null");
        this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
    }

    public ScheduleBaseline execute(PlanId planId, String name, String createdBy) {
        var plan = repository.findById(Objects.requireNonNull(planId, "Plan id cannot be null"))
                .orElseThrow(() -> new IllegalArgumentException("Schedule plan was not found"));
        var baseline = plan.createBaseline(name, clock.instant(), createdBy);
        repository.save(plan);
        return baseline;
    }
}
