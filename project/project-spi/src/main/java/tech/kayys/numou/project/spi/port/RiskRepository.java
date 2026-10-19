package tech.kayys.numou.project.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.project.domain.project.ProjectId;
import tech.kayys.numou.project.domain.risk.Risk;
import tech.kayys.numou.project.domain.risk.RiskId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Persistence port for risks. Risks are their own aggregate —
 * never loaded through {@code Project}.
 */
public interface RiskRepository
        extends Repository<Risk, RiskId> {

    CompletionStage<Optional<Risk>> findByNumber(ProjectId projectId, String number);

    CompletionStage<List<Risk>> findOpenByProjectId(ProjectId projectId);
}
