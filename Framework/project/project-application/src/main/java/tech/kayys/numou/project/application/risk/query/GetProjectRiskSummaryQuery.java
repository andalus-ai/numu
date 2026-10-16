package tech.kayys.numou.project.application.risk.query;

import tech.kayys.numou.foundation.application.query.Query;
import tech.kayys.numou.project.domain.project.ProjectId;

import java.util.Objects;

/** Aggregated risk dashboard counters for one project. */
public record GetProjectRiskSummaryQuery(
        ProjectId projectId
) implements Query {

    public GetProjectRiskSummaryQuery {
        Objects.requireNonNull(projectId, "projectId cannot be null");
    }
}
