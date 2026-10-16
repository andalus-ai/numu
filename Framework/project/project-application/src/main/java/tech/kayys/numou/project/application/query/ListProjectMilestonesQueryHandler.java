package tech.kayys.syirkah.project.application.query;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.query.QueryHandler;
import tech.kayys.syirkah.project.spi.port.ProjectMilestoneRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Lists a project's milestones ordered by sequence. */
public final class ListProjectMilestonesQueryHandler
        implements QueryHandler<ListProjectMilestonesQuery, List<ProjectMilestoneView>> {

    private final ProjectMilestoneRepository milestones;

    public ListProjectMilestonesQueryHandler(ProjectMilestoneRepository milestones) {
        this.milestones = Objects.requireNonNull(milestones);
    }

    @Override
    public Uni<List<ProjectMilestoneView>> handle(ListProjectMilestonesQuery query) {
        return Uni.createFrom()
                .completionStage(milestones.findByProjectId(query.projectId()))
                .map(found -> found.stream()
                        .sorted(Comparator.comparingInt(milestone -> milestone.sequence()))
                        .map(ProjectMilestoneView::from)
                        .toList());
    }
}
