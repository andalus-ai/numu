package tech.kayys.numou.project.application.risk.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.project.domain.risk.RiskTreatmentActionId;

import java.util.Objects;

public record CompleteRiskTreatmentActionCommand(
        RiskTreatmentActionId actionId
) implements Command {

    public CompleteRiskTreatmentActionCommand {
        Objects.requireNonNull(actionId, "actionId cannot be null");
    }
}
