package tech.kayys.syirkah.construction.spi.hse;

import tech.kayys.syirkah.construction.domain.hse.PermitToWork;
import tech.kayys.syirkah.construction.domain.hse.PermitToWorkId;
import tech.kayys.syirkah.foundation.domain.repository.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface PermitToWorkRepository extends Repository<PermitToWork, PermitToWorkId> {
    CompletionStage<List<PermitToWork>> findBySiteId(UUID siteId);
}
