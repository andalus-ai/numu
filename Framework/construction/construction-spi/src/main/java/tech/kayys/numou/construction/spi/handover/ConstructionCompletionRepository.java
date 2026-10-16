package tech.kayys.syirkah.construction.spi.handover;

import tech.kayys.syirkah.construction.domain.handover.ConstructionCompletion;
import tech.kayys.syirkah.construction.domain.handover.ConstructionCompletionId;
import tech.kayys.syirkah.foundation.domain.repository.Repository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface ConstructionCompletionRepository extends Repository<ConstructionCompletion, ConstructionCompletionId> {
    CompletionStage<Optional<ConstructionCompletion>> findByProjectId(UUID projectId);
}
