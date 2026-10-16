package tech.kayys.syirkah.construction.spi.cost;

import tech.kayys.syirkah.construction.domain.cost.ConstructionCostEntry;
import tech.kayys.syirkah.construction.domain.cost.ConstructionCostEntryId;
import tech.kayys.syirkah.foundation.domain.repository.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface ConstructionCostEntryRepository extends Repository<ConstructionCostEntry, ConstructionCostEntryId> {
    CompletionStage<List<ConstructionCostEntry>> findByProjectId(UUID projectId);
}
