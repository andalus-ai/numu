package tech.kayys.syirkah.project.application.risk.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.project.domain.risk.RiskId;

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
