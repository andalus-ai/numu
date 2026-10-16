package tech.kayys.syirkah.project.application.risk.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.project.domain.risk.RiskId;
import tech.kayys.syirkah.project.domain.risk.RiskResponse;

import java.util.Objects;

public record PlanRiskResponseCommand(
        RiskId riskId,
        RiskResponse response
) implements Command {

    public PlanRiskResponseCommand {
        Objects.requireNonNull(riskId, "riskId cannot be null");
        Objects.requireNonNull(response, "response cannot be null");
    }
}
