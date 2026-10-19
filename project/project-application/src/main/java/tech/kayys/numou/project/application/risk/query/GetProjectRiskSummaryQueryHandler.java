package tech.kayys.numou.project.application.risk.query;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.query.QueryHandler;
import tech.kayys.numou.project.domain.project.ProjectId;
import tech.kayys.numou.project.spi.port.ProjectRiskQueryRepository;
import tech.kayys.numou.project.spi.port.ProjectRiskSummary;

import java.util.Objects;

/**
 * Serves the dashboard counters from the read model — never by
 * loading every risk/issue aggregate.
 */
public final class GetProjectRiskSummaryQueryHandler
        implements QueryHandler<GetProjectRiskSummaryQuery, ProjectRiskSummary> {

    private final ProjectRiskQueryRepository queryRepository;

    public GetProjectRiskSummaryQueryHandler(
            ProjectRiskQueryRepository queryRepository
    ) {
        this.queryRepository = Objects.requireNonNull(queryRepository);
    }

    @Override
    public Uni<ProjectRiskSummary> handle(GetProjectRiskSummaryQuery query) {
        return Uni.createFrom()
                .completionStage(queryRepository.findSummary(query.projectId()));
    }
}
