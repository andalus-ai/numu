package tech.kayys.syirkah.project.application.query;

import tech.kayys.syirkah.foundation.application.query.Query;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.util.Objects;

/** Reads a single project by identity. */
public record GetProjectQuery(
        ProjectId projectId
) implements Query {

    public GetProjectQuery {
        Objects.requireNonNull(projectId, "projectId cannot be null");
    }
}
