package tech.kayys.syirkah.construction.spi.risk;

import tech.kayys.syirkah.construction.domain.risk.ConstructionClaim;
import tech.kayys.syirkah.construction.domain.risk.ConstructionClaimId;
import tech.kayys.syirkah.foundation.domain.repository.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface ConstructionClaimRepository extends Repository<ConstructionClaim, ConstructionClaimId> {
    CompletionStage<List<ConstructionClaim>> findByContractId(UUID contractId);
}
