package tech.kayys.numou.project.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.project.domain.commercial.ProjectClaim;
import tech.kayys.numou.project.domain.commercial.ProjectClaimId;
import tech.kayys.numou.project.domain.project.ProjectId;

import java.util.List;
import java.util.concurrent.CompletionStage;

/** Persistence port for project claims. */
public interface ProjectClaimRepository
        extends Repository<ProjectClaim, ProjectClaimId> {

    CompletionStage<List<ProjectClaim>> findByProjectId(
            ProjectId projectId
    );
}