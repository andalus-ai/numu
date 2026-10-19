package tech.kayys.numou.project.application.query;

import tech.kayys.numou.foundation.application.query.Query;
import tech.kayys.numou.project.domain.project.ProjectId;

import java.util.Objects;

/** Lists the tasks of one project. */
public record ListProjectTasksQuery(
        ProjectId projectId
) implements Query {

    public ListProjectTasksQuery {
        Objects.requireNonNull(projectId, "projectId cannot be null");
    }
}
