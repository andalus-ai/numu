package tech.kayys.syirkah.project.application.query;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.query.QueryHandler;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.project.spi.port.ProjectRepository;

import java.util.Objects;

/** Serves the project read model without exposing domain internals. */
public final class GetProjectQueryHandler
        implements QueryHandler<GetProjectQuery, ProjectView> {

    private final ProjectRepository projects;

    public GetProjectQueryHandler(ProjectRepository projects) {
        this.projects = Objects.requireNonNull(projects);
    }

    @Override
    public Uni<ProjectView> handle(GetProjectQuery query) {
        return Uni.createFrom()
                .completionStage(projects.findById(query.projectId()))
                .map(maybeProject -> maybeProject
                        .map(ProjectView::from)
                        .orElseThrow(() -> ApplicationError.of(
                                "PROJECT_NOT_FOUND",
                                "Project does not exist"
                        ).toException())
                );
    }
}
