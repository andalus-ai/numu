package tech.kayys.numou.project.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.project.domain.commercial.ProjectRetention;
import tech.kayys.numou.project.domain.commercial.ProjectRetentionId;
import tech.kayys.numou.project.domain.commercial.RetentionStatus;
import tech.kayys.numou.project.domain.project.ProjectId;

import java.util.List;
import java.util.concurrent.CompletionStage;

/** Persistence port for retentions. */
public interface ProjectRetentionRepository
        extends Repository<ProjectRetention, ProjectRetentionId> {

    CompletionStage<List<ProjectRetention>> findByProjectId(
            ProjectId projectId
    );

    /** Retentions that still hold a balance (HELD / PARTIALLY_RELEASED). */
    CompletionStage<List<ProjectRetention>> findOpenByProjectId(
            ProjectId projectId
    );
}