package tech.kayys.syirkah.project.application.risk.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.project.domain.risk.RiskId;

import java.util.Objects;

public record StartRiskMonitoringCommand(
        RiskId riskId
) implements Command {

    public StartRiskMonitoringCommand {
        Objects.requireNonNull(riskId, "riskId cannot be null");
    }
}
