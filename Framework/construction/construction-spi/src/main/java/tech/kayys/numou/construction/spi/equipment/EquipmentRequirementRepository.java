package tech.kayys.numou.construction.spi.equipment;

import tech.kayys.numou.construction.domain.equipment.EquipmentRequirement;
import tech.kayys.numou.construction.domain.equipment.EquipmentRequirementId;
import tech.kayys.numou.foundation.domain.repository.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface EquipmentRequirementRepository extends Repository<EquipmentRequirement, EquipmentRequirementId> {
    CompletionStage<List<EquipmentRequirement>> findByProjectId(UUID projectId);
}
