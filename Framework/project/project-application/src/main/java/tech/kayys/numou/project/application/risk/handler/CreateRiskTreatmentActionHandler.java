package tech.kayys.syirkah.project.application.risk.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.project.application.risk.RiskErrors;
import tech.kayys.syirkah.project.application.risk.command.CreateRiskTreatmentActionCommand;
import tech.kayys.syirkah.project.domain.risk.RiskTreatmentAction;
import tech.kayys.syirkah.project.domain.risk.RiskTreatmentActionId;
import tech.kayys.syirkah.project.spi.port.RiskRepository;
import tech.kayys.syirkah.project.spi.port.RiskTreatmentActionRepository;

import java.util.Objects;

/**
 * Adds a treatment action to an existing risk. The risk reference in
 * the payload is verified, not trusted.
 */
public final class CreateRiskTreatmentActionHandler
        implements CommandHandler<CreateRiskTreatmentActionCommand, Result<RiskTreatmentActionId>> {

    private static final ApplicationError RISK_NOT_FOUND =
            ApplicationError.of(RiskErrors.RISK_NOT_FOUND, "Risk does not exist");

    private final RiskRepository risks;
    private final RiskTreatmentActionRepository actions;

    public CreateRiskTreatmentActionHandler(
            RiskRepository risks,
            RiskTreatmentActionRepository actions
    ) {
        this.risks = Objects.requireNonNull(risks);
        this.actions = Objects.requireNonNull(actions);
    }

    @Override
    public Uni<Result<RiskTreatmentActionId>> handle(
            CreateRiskTreatmentActionCommand command
    ) {
        return Uni.createFrom()
                .completionStage(risks.findById(command.riskId()))
                .onItem()
                .transformToUni(maybeRisk -> {

                    if (maybeRisk.isEmpty()) {
                        return Uni.createFrom().item(Result.failure(RISK_NOT_FOUND));
                    }

                    if (!maybeRisk.get().projectId().equals(command.projectId())) {
                        return Uni.createFrom().item(Result.failure(RISK_NOT_FOUND));
                    }

                    var action = RiskTreatmentAction.create(
                            RiskTreatmentActionId.generate(),
                            command.projectId(),
                            command.riskId(),
                            command.title(),
                            command.description(),
                            command.ownerId(),
                            command.dueDate(),
                            command.estimatedCost()
                    );

                    return Uni.createFrom()
                            .completionStage(actions.save(action))
                            .map(saved -> Result.success(saved.id()));
                });
    }
}
