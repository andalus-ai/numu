package tech.kayys.syirkah.project.application.risk.query;

import tech.kayys.syirkah.foundation.application.query.Query;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.util.Objects;

/** Aggregated risk dashboard counters for one project. */
public record GetProjectRiskSummaryQuery(
        ProjectId projectId
) implements Query {

    public GetProjectRiskSummaryQuery {
        Objects.requireNonNull(projectId, "projectId cannot be null");
    }
}
