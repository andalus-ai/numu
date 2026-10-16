package tech.kayys.numou.construction.spi.workforce;

import tech.kayys.numou.construction.domain.workforce.LaborRequirement;
import tech.kayys.numou.construction.domain.workforce.LaborRequirementId;
import tech.kayys.numou.foundation.domain.repository.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface LaborRequirementRepository extends Repository<LaborRequirement, LaborRequirementId> {
    CompletionStage<List<LaborRequirement>> findByProjectId(UUID projectId);
}
