package tech.kayys.numou.project.application.risk.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.project.domain.risk.ImpactLevel;
import tech.kayys.numou.project.domain.risk.Probability;
import tech.kayys.numou.project.domain.risk.RiskId;

import java.util.Objects;

public record AssessRiskCommand(
        RiskId riskId,
        Probability probability,
        ImpactLevel impact
) implements Command {

    public AssessRiskCommand {
        Objects.requireNonNull(riskId, "riskId cannot be null");
        Objects.requireNonNull(probability, "probability cannot be null");
        Objects.requireNonNull(impact, "impact cannot be null");
    }
}
