package tech.kayys.numou.project.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.project.domain.project.ProjectId;
import tech.kayys.numou.project.domain.task.ProjectTask;
import tech.kayys.numou.project.domain.task.ProjectTaskId;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Persistence port for project tasks.
 *
 * Tasks are their own aggregate roots, linked to the project by
 * identifier - which is why they get their own repository instead
 * of being loaded through the Project aggregate.
 */
public interface ProjectTaskRepository
        extends Repository<ProjectTask, ProjectTaskId> {

    CompletionStage<List<ProjectTask>> findByProjectId(ProjectId projectId);
}
