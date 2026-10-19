package tech.kayys.numou.construction.spi.contract;

import tech.kayys.numou.construction.domain.contract.ConstructionContract;
import tech.kayys.numou.construction.domain.contract.ConstructionContractId;
import tech.kayys.numou.foundation.domain.repository.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface ConstructionContractRepository extends Repository<ConstructionContract, ConstructionContractId> {
    CompletionStage<List<ConstructionContract>> findByProjectId(UUID projectId);
    CompletionStage<Optional<ConstructionContract>> findByContractNumber(UUID projectId, String contractNumber);
}
