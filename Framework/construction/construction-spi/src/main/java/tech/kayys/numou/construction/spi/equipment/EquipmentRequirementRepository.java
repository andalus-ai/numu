package tech.kayys.syirkah.construction.spi.equipment;

import tech.kayys.syirkah.construction.domain.equipment.EquipmentRequirement;
import tech.kayys.syirkah.construction.domain.equipment.EquipmentRequirementId;
import tech.kayys.syirkah.foundation.domain.repository.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface EquipmentRequirementRepository extends Repository<EquipmentRequirement, EquipmentRequirementId> {
    CompletionStage<List<EquipmentRequirement>> findByProjectId(UUID projectId);
}
