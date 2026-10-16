package tech.kayys.numou.project.application.risk.query;

import tech.kayys.numou.foundation.application.query.Query;
import tech.kayys.numou.project.domain.project.ProjectId;

import java.util.Objects;

/** The risk register (one row per risk) for one project. */
public record ListRiskRegisterQuery(
        ProjectId projectId
) implements Query {

    public ListRiskRegisterQuery {
        Objects.requireNonNull(projectId, "projectId cannot be null");
    }
}
