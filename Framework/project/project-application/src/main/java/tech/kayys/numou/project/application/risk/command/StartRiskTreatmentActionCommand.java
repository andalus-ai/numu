package tech.kayys.syirkah.project.application.risk.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.project.domain.risk.RiskTreatmentActionId;

import java.util.Objects;

public record StartRiskTreatmentActionCommand(
        RiskTreatmentActionId actionId
) implements Command {

    public StartRiskTreatmentActionCommand {
        Objects.requireNonNull(actionId, "actionId cannot be null");
    }
}
