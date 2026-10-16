package tech.kayys.numou.project.spi.port;

import tech.kayys.numou.project.domain.project.ProjectId;
import tech.kayys.numou.project.domain.risk.Issue;
import tech.kayys.numou.project.domain.risk.RiskStatus;
import tech.kayys.numou.project.domain.risk.RiskCategory;
import tech.kayys.numou.project.domain.risk.Probability;
import tech.kayys.numou.project.domain.risk.ImpactLevel;
import tech.kayys.numou.project.domain.risk.RiskResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Read side of risk management. Never compute the dashboard by
 * loading every aggregate: these are projections (in-memory in the
 * adapter, SQL read models in production).
 */
public interface ProjectRiskQueryRepository {

    CompletionStage<ProjectRiskSummary> findSummary(ProjectId projectId);

    CompletionStage<List<RiskRegisterRow>> findRiskRegister(ProjectId projectId);

    CompletionStage<List<Issue>> findOpenIssues(ProjectId projectId);
}
