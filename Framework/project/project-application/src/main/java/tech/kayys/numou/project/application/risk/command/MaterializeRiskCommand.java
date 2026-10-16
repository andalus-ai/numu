package tech.kayys.numou.project.application.risk.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.project.domain.risk.RiskId;

import java.util.Objects;

/**
 * Marks the risk as having happened. The resulting
 * {@code RiskMaterialized} event is what drives issue creation — this
 * command never creates the Issue itself.
 */
public record MaterializeRiskCommand(
        RiskId riskId
) implements Command {

    public MaterializeRiskCommand {
        Objects.requireNonNull(riskId, "riskId cannot be null");
    }
}
