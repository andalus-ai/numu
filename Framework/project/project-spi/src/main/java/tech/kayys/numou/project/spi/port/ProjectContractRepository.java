package tech.kayys.numou.project.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.project.domain.commercial.ProjectContract;
import tech.kayys.numou.project.domain.commercial.ProjectContractId;
import tech.kayys.numou.project.domain.project.ProjectId;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Persistence port for project contracts.
 *
 * A project has at most one contract per tenant in the first
 * version, so lookup by project is offered as well.
 */
public interface ProjectContractRepository
        extends Repository<ProjectContract, ProjectContractId> {

    CompletionStage<Optional<ProjectContract>> findByProjectId(
            ProjectId projectId
    );
}