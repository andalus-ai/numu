package tech.kayys.numou.project.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.project.domain.project.ProjectId;
import tech.kayys.numou.project.domain.risk.RiskId;
import tech.kayys.numou.project.domain.risk.RiskTreatmentAction;
import tech.kayys.numou.project.domain.risk.RiskTreatmentActionId;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Treatment actions are persisted independently of the risk they
 * belong to — one risk accumulates many actions over months.
 */
public interface RiskTreatmentActionRepository
        extends Repository<RiskTreatmentAction, RiskTreatmentActionId> {

    CompletionStage<List<RiskTreatmentAction>> findByRiskId(RiskId riskId);

    CompletionStage<List<RiskTreatmentAction>> findOpenByProjectId(ProjectId projectId);
}
