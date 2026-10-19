package tech.kayys.numou.construction.spi.cost;

import tech.kayys.numou.construction.domain.cost.ConstructionCostEntry;
import tech.kayys.numou.construction.domain.cost.ConstructionCostEntryId;
import tech.kayys.numou.foundation.domain.repository.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface ConstructionCostEntryRepository extends Repository<ConstructionCostEntry, ConstructionCostEntryId> {
    CompletionStage<List<ConstructionCostEntry>> findByProjectId(UUID projectId);
}
