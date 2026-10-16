package tech.kayys.numou.construction.spi.risk;

import tech.kayys.numou.construction.domain.risk.ConstructionClaim;
import tech.kayys.numou.construction.domain.risk.ConstructionClaimId;
import tech.kayys.numou.foundation.domain.repository.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface ConstructionClaimRepository extends Repository<ConstructionClaim, ConstructionClaimId> {
    CompletionStage<List<ConstructionClaim>> findByContractId(UUID contractId);
}
