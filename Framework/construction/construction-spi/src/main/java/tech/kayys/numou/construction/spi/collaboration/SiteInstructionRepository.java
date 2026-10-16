package tech.kayys.numou.construction.spi.collaboration;

import tech.kayys.numou.construction.domain.collaboration.SiteInstruction;
import tech.kayys.numou.construction.domain.collaboration.SiteInstructionId;
import tech.kayys.numou.foundation.domain.repository.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface SiteInstructionRepository extends Repository<SiteInstruction, SiteInstructionId> {
    CompletionStage<List<SiteInstruction>> findBySiteId(UUID siteId);
}
