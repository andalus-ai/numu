package tech.kayys.syirkah.project.application.risk.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.project.application.risk.RiskErrors;
import tech.kayys.syirkah.project.application.risk.command.AssessRiskCommand;
import tech.kayys.syirkah.project.application.risk.command.CloseRiskCommand;
import tech.kayys.syirkah.project.application.risk.command.MaterializeRiskCommand;
import tech.kayys.syirkah.project.application.risk.command.PlanRiskResponseCommand;
import tech.kayys.syirkah.project.application.risk.command.StartRiskMonitoringCommand;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.risk.Risk;
import tech.kayys.syirkah.project.domain.risk.RiskId;
import tech.kayys.syirkah.project.spi.port.RiskRepository;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Applies one risk lifecycle transition and publishes the risk events.
 *
 * Illegal transitions are rejected by the aggregate itself
 * (InvalidRiskStateException); the handler only deals with the
 * missing-risk case and persistence.
 */
public final class ChangeRiskStateHandler {

    private static final ApplicationError NOT_FOUND =
            ApplicationError.of(RiskErrors.RISK_NOT_FOUND, "Risk does not exist");

    private final RiskRepository risks;
    private final EventPublisher eventPublisher;

    public ChangeRiskStateHandler(
            RiskRepository risks,
            EventPublisher eventPublisher
    ) {
        this.risks = Objects.requireNonNull(risks);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    public Uni<Result<RiskId>> assess(AssessRiskCommand command) {
        return transition(command.riskId(),
                risk -> risk.assess(command.probability(), command.impact()));
    }

    public Uni<Result<RiskId>> planResponse(PlanRiskResponseCommand command) {
        return transition(command.riskId(),
                risk -> risk.planResponse(command.response()));
    }

    public Uni<Result<RiskId>> startMonitoring(StartRiskMonitoringCommand command) {
        return transition(command.riskId(), Risk::monitor);
    }

    public Uni<Result<RiskId>> materialize(MaterializeRiskCommand command) {
        return transition(command.riskId(), Risk::materialize);
    }

    public Uni<Result<RiskId>> close(CloseRiskCommand command) {
        return transition(command.riskId(), Risk::close);
    }

    private Uni<Result<RiskId>> transition(
            RiskId riskId,
            Consumer<Risk> transition
    ) {
        return Uni.createFrom()
                .completionStage(risks.findById(riskId))
                .onItem()
                .transformToUni(maybeRisk -> {

                    if (maybeRisk.isEmpty()) {
                        return Uni.createFrom().item(Result.failure(NOT_FOUND));
                    }

                    var risk = maybeRisk.get();
                    transition.accept(risk);

                    return Uni.createFrom()
                            .completionStage(risks.save(risk))
                            .onItem()
                            .transformToUni(saved -> eventPublisher
                                    .publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved.id())));
                });
    }
}
