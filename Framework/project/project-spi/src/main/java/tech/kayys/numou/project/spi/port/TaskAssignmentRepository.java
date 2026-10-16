package tech.kayys.syirkah.project.spi.port;

import tech.kayys.syirkah.foundation.domain.repository.Repository;
import tech.kayys.syirkah.project.domain.task.ProjectTaskId;
import tech.kayys.syirkah.project.domain.task.TaskAssignment;
import tech.kayys.syirkah.project.domain.task.TaskAssignmentId;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface TaskAssignmentRepository
        extends Repository<TaskAssignment, TaskAssignmentId> {

    CompletionStage<List<TaskAssignment>> findByTaskId(ProjectTaskId taskId);
}
