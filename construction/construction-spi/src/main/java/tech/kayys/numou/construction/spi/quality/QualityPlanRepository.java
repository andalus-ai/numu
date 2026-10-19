package tech.kayys.numou.construction.spi.quality;

import tech.kayys.numou.construction.domain.quality.QualityPlan;
import tech.kayys.numou.construction.domain.quality.QualityPlanId;
import tech.kayys.numou.foundation.domain.repository.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface QualityPlanRepository extends Repository<QualityPlan, QualityPlanId> {
    CompletionStage<List<QualityPlan>> findByProjectId(UUID projectId);
}
