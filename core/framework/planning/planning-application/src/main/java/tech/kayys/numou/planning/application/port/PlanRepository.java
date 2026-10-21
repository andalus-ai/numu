package tech.kayys.numou.planning.application.port;

import tech.kayys.numou.planning.domain.PlanId;
import tech.kayys.numou.planning.domain.SchedulePlan;

import java.util.Optional;

/** Persistence port; implementations must persist plan and baselines atomically. */
public interface PlanRepository {
    Optional<SchedulePlan> findById(PlanId id);
    boolean existsByCode(String code);
    void save(SchedulePlan plan);
}
