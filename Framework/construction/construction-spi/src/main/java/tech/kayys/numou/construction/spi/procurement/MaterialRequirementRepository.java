package tech.kayys.numou.construction.spi.procurement;

import tech.kayys.numou.construction.domain.procurement.MaterialRequirement;
import tech.kayys.numou.construction.domain.procurement.MaterialRequirementId;
import tech.kayys.numou.foundation.domain.repository.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface MaterialRequirementRepository extends Repository<MaterialRequirement, MaterialRequirementId> {
    CompletionStage<List<MaterialRequirement>> findByProjectId(UUID projectId);
}
