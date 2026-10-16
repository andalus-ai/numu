package tech.kayys.numou.construction.spi.handover;

import tech.kayys.numou.construction.domain.handover.ConstructionCompletion;
import tech.kayys.numou.construction.domain.handover.ConstructionCompletionId;
import tech.kayys.numou.foundation.domain.repository.Repository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface ConstructionCompletionRepository extends Repository<ConstructionCompletion, ConstructionCompletionId> {
    CompletionStage<Optional<ConstructionCompletion>> findByProjectId(UUID projectId);
}
