package tech.kayys.syirkah.construction.spi.workforce;

import tech.kayys.syirkah.construction.domain.workforce.LaborRequirement;
import tech.kayys.syirkah.construction.domain.workforce.LaborRequirementId;
import tech.kayys.syirkah.foundation.domain.repository.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface LaborRequirementRepository extends Repository<LaborRequirement, LaborRequirementId> {
    CompletionStage<List<LaborRequirement>> findByProjectId(UUID projectId);
}
