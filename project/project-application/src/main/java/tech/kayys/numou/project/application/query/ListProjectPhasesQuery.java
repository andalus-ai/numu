package tech.kayys.numou.project.application.query;

import tech.kayys.numou.foundation.application.query.Query;
import tech.kayys.numou.project.domain.project.ProjectId;

import java.util.Objects;

/** Lists the phases of one project. */
public record ListProjectPhasesQuery(
        ProjectId projectId
) implements Query {

    public ListProjectPhasesQuery {
        Objects.requireNonNull(projectId, "projectId cannot be null");
    }
}
