package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.workforce.domain.talent.TalentReview;
import tech.kayys.numou.workforce.domain.talent.TalentReviewId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface TalentReviewRepository extends Repository<TalentReview, TalentReviewId> {
    CompletionStage<List<TalentReview>> findByWorker(WorkerId workerId);
}
