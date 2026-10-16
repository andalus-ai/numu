package tech.kayys.numou.project.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.project.domain.task.ProjectTaskId;
import tech.kayys.numou.project.domain.task.TaskAssignment;
import tech.kayys.numou.project.domain.task.TaskAssignmentId;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface TaskAssignmentRepository
        extends Repository<TaskAssignment, TaskAssignmentId> {

    CompletionStage<List<TaskAssignment>> findByTaskId(ProjectTaskId taskId);
}
