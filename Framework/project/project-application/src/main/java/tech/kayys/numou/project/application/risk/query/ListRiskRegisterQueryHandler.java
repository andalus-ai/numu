package tech.kayys.numou.project.application.risk.query;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.query.QueryHandler;
import tech.kayys.numou.project.spi.port.ProjectRiskQueryRepository;
import tech.kayys.numou.project.spi.port.RiskRegisterRow;

import java.util.List;
import java.util.Objects;

public final class ListRiskRegisterQueryHandler
        implements QueryHandler<ListRiskRegisterQuery, List<RiskRegisterRow>> {

    private final ProjectRiskQueryRepository queryRepository;

    public ListRiskRegisterQueryHandler(
            ProjectRiskQueryRepository queryRepository
    ) {
        this.queryRepository = Objects.requireNonNull(queryRepository);
    }

    @Override
    public Uni<List<RiskRegisterRow>> handle(ListRiskRegisterQuery query) {
        return Uni.createFrom()
                .completionStage(queryRepository.findRiskRegister(query.projectId()));
    }
}
