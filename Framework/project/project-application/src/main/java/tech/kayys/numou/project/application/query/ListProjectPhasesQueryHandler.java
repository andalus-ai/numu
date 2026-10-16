package tech.kayys.syirkah.project.application.query;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.query.QueryHandler;
import tech.kayys.syirkah.project.spi.port.ProjectPhaseRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Lists a project's phases ordered by sequence, so callers never see
 * the aggregate-internal ordering.
 */
public final class ListProjectPhasesQueryHandler
        implements QueryHandler<ListProjectPhasesQuery, List<ProjectPhaseView>> {

    private final ProjectPhaseRepository phases;

    public ListProjectPhasesQueryHandler(ProjectPhaseRepository phases) {
        this.phases = Objects.requireNonNull(phases);
    }

    @Override
    public Uni<List<ProjectPhaseView>> handle(ListProjectPhasesQuery query) {
        return Uni.createFrom()
                .completionStage(phases.findByProjectId(query.projectId()))
                .map(found -> found.stream()
                        .sorted(Comparator.comparingInt(phase -> phase.sequence()))
                        .map(ProjectPhaseView::from)
                        .toList());
    }
}
