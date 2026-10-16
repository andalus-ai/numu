package tech.kayys.numou.project.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.project.domain.project.ProjectId;
import tech.kayys.numou.project.domain.task.TaskDependency;
import tech.kayys.numou.project.domain.task.TaskDependencyId;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface TaskDependencyRepository
        extends Repository<TaskDependency, TaskDependencyId> {

    CompletionStage<List<TaskDependency>> findByProjectId(ProjectId projectId);
}
