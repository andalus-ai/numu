package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.workforce.domain.development.DevelopmentNeed;
import tech.kayys.numou.workforce.domain.development.DevelopmentNeedId;
import tech.kayys.numou.workforce.domain.development.DevelopmentPlanId;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface DevelopmentNeedRepository extends Repository<DevelopmentNeed, DevelopmentNeedId> {
    CompletionStage<List<DevelopmentNeed>> findByPlan(DevelopmentPlanId planId);
}
