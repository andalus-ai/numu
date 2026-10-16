package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.workforce.domain.development.DevelopmentActivity;
import tech.kayys.numou.workforce.domain.development.DevelopmentActivityId;
import tech.kayys.numou.workforce.domain.development.DevelopmentNeedId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface DevelopmentActivityRepository extends Repository<DevelopmentActivity, DevelopmentActivityId> {
    CompletionStage<List<DevelopmentActivity>> findByNeed(DevelopmentNeedId needId);
    CompletionStage<List<DevelopmentActivity>> findByWorker(WorkerId workerId);
}
