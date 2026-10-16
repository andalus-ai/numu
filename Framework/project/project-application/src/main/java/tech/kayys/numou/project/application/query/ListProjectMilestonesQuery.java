package tech.kayys.syirkah.project.application.query;

import tech.kayys.syirkah.foundation.application.query.Query;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.util.Objects;

/** Lists the milestones of one project. */
public record ListProjectMilestonesQuery(
        ProjectId projectId
) implements Query {

    public ListProjectMilestonesQuery {
        Objects.requireNonNull(projectId, "projectId cannot be null");
    }
}
