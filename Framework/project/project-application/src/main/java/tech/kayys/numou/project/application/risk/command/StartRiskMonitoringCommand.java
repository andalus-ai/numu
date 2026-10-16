package tech.kayys.numou.project.application.risk.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.project.domain.risk.RiskId;

import java.util.Objects;

public record StartRiskMonitoringCommand(
        RiskId riskId
) implements Command {

    public StartRiskMonitoringCommand {
        Objects.requireNonNull(riskId, "riskId cannot be null");
    }
}
