package tech.kayys.syirkah.project.application.query;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.query.QueryHandler;
import tech.kayys.syirkah.project.spi.port.ProjectTaskRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Lists a project's tasks, so callers never see the
 * aggregate-internal ordering.
 */
public final class ListProjectTasksQueryHandler
        implements QueryHandler<ListProjectTasksQuery, List<ProjectTaskView>> {

    private final ProjectTaskRepository tasks;

    public ListProjectTasksQueryHandler(ProjectTaskRepository tasks) {
        this.tasks = Objects.requireNonNull(tasks);
    }

    @Override
    public Uni<List<ProjectTaskView>> handle(ListProjectTasksQuery query) {
        return Uni.createFrom()
                .completionStage(tasks.findByProjectId(query.projectId()))
                .map(found -> found.stream()
                        .sorted(Comparator.comparing(task -> task.taskNumber().value()))
                        .map(ProjectTaskView::from)
                        .toList());
    }
}
