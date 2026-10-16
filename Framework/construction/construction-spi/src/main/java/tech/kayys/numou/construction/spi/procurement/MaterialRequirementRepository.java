package tech.kayys.syirkah.construction.spi.procurement;

import tech.kayys.syirkah.construction.domain.procurement.MaterialRequirement;
import tech.kayys.syirkah.construction.domain.procurement.MaterialRequirementId;
import tech.kayys.syirkah.foundation.domain.repository.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface MaterialRequirementRepository extends Repository<MaterialRequirement, MaterialRequirementId> {
    CompletionStage<List<MaterialRequirement>> findByProjectId(UUID projectId);
}
