package tech.kayys.syirkah.project.application.risk.query;

import tech.kayys.syirkah.foundation.application.query.Query;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.util.Objects;

/** The risk register (one row per risk) for one project. */
public record ListRiskRegisterQuery(
        ProjectId projectId
) implements Query {

    public ListRiskRegisterQuery {
        Objects.requireNonNull(projectId, "projectId cannot be null");
    }
}
