package tech.kayys.syirkah.construction.spi.planning;

import tech.kayys.syirkah.construction.domain.planning.ConstructionPlan;
import tech.kayys.syirkah.construction.domain.planning.ConstructionPlanId;
import tech.kayys.syirkah.foundation.domain.repository.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface ConstructionPlanRepository extends Repository<ConstructionPlan, ConstructionPlanId> {
    CompletionStage<List<ConstructionPlan>> findByProjectId(UUID projectId);
}
