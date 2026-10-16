package tech.kayys.syirkah.construction.spi.collaboration;

import tech.kayys.syirkah.construction.domain.collaboration.SiteInstruction;
import tech.kayys.syirkah.construction.domain.collaboration.SiteInstructionId;
import tech.kayys.syirkah.foundation.domain.repository.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface SiteInstructionRepository extends Repository<SiteInstruction, SiteInstructionId> {
    CompletionStage<List<SiteInstruction>> findBySiteId(UUID siteId);
}
