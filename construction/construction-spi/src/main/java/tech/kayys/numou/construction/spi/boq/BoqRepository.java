package tech.kayys.numou.construction.spi.boq;

import tech.kayys.numou.construction.domain.boq.Boq;
import tech.kayys.numou.construction.domain.boq.BoqId;
import tech.kayys.numou.foundation.domain.repository.Repository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface BoqRepository extends Repository<Boq, BoqId> {
    CompletionStage<Optional<Boq>> findByProjectId(UUID projectId);
}
