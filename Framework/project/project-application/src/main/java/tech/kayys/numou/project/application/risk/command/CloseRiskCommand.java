package tech.kayys.syirkah.project.application.risk.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.project.domain.risk.RiskId;

import java.util.Objects;

/**
 * Closes a risk that no longer applies or has run its course.
 * Closure is terminal — there is no reopen.
 */
public record CloseRiskCommand(
        RiskId riskId
) implements Command {

    public CloseRiskCommand {
        Objects.requireNonNull(riskId, "riskId cannot be null");
    }
}
