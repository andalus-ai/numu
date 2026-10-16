package tech.kayys.syirkah.project.application.risk.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.project.application.risk.RiskErrors;
import tech.kayys.syirkah.project.application.risk.command.CancelRiskTreatmentActionCommand;
import tech.kayys.syirkah.project.application.risk.command.CompleteRiskTreatmentActionCommand;
import tech.kayys.syirkah.project.application.risk.command.StartRiskTreatmentActionCommand;
import tech.kayys.syirkah.project.domain.risk.RiskTreatmentAction;
import tech.kayys.syirkah.project.domain.risk.RiskTreatmentActionId;
import tech.kayys.syirkah.project.spi.port.RiskTreatmentActionRepository;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Applies one treatment-action transition. Illegal transitions throw
 * InvalidRiskStateException from the aggregate.
 */
public final class ChangeRiskActionStateHandler {

    private static final ApplicationError NOT_FOUND =
            ApplicationError.of(
                    RiskErrors.RISK_ACTION_NOT_FOUND,
                    "Risk treatment action does not exist"
            );

    private final RiskTreatmentActionRepository actions;

    public ChangeRiskActionStateHandler(RiskTreatmentActionRepository actions) {
        this.actions = Objects.requireNonNull(actions);
    }

    public Uni<Result<RiskTreatmentActionId>> start(
            StartRiskTreatmentActionCommand command
    ) {
        return transition(command.actionId(), RiskTreatmentAction::start);
    }

    public Uni<Result<RiskTreatmentActionId>> complete(
            CompleteRiskTreatmentActionCommand command
    ) {
        return transition(command.actionId(), RiskTreatmentAction::complete);
    }

    public Uni<Result<RiskTreatmentActionId>> cancel(
            CancelRiskTreatmentActionCommand command
    ) {
        return transition(command.actionId(), RiskTreatmentAction::cancel);
    }

    private Uni<Result<RiskTreatmentActionId>> transition(
            RiskTreatmentActionId actionId,
            Consumer<RiskTreatmentAction> transition
    ) {
        return Uni.createFrom()
                .completionStage(actions.findById(actionId))
                .onItem()
                .transformToUni(maybeAction -> {

                    if (maybeAction.isEmpty()) {
                        return Uni.createFrom().item(Result.failure(NOT_FOUND));
                    }

                    var action = maybeAction.get();
                    transition.accept(action);

                    return Uni.createFrom()
                            .completionStage(actions.save(action))
                            .map(saved -> Result.success(saved.id()));
                });
    }
}
