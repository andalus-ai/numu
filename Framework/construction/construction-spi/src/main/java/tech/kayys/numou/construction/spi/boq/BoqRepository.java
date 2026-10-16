package tech.kayys.syirkah.construction.spi.boq;

import tech.kayys.syirkah.construction.domain.boq.Boq;
import tech.kayys.syirkah.construction.domain.boq.BoqId;
import tech.kayys.syirkah.foundation.domain.repository.Repository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface BoqRepository extends Repository<Boq, BoqId> {
    CompletionStage<Optional<Boq>> findByProjectId(UUID projectId);
}
