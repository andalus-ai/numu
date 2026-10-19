package tech.kayys.numou.project.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.project.domain.commercial.ProjectAdvance;
import tech.kayys.numou.project.domain.commercial.ProjectAdvanceId;
import tech.kayys.numou.project.domain.project.ProjectId;

import java.util.List;
import java.util.concurrent.CompletionStage;

/** Persistence port for advances. */
public interface ProjectAdvanceRepository
        extends Repository<ProjectAdvance, ProjectAdvanceId> {

    CompletionStage<List<ProjectAdvance>> findByProjectId(
            ProjectId projectId
    );
}