package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.workforce.domain.learning.LearningOfferingId;
import tech.kayys.numou.workforce.domain.learning.LearningSession;
import tech.kayys.numou.workforce.domain.learning.LearningSessionId;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface LearningSessionRepository extends Repository<LearningSession, LearningSessionId> {
    CompletionStage<List<LearningSession>> findByOffering(LearningOfferingId offeringId);
}
