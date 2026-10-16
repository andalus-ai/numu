package tech.kayys.numou.finance.treasury.application.api.command;

import tech.kayys.numou.finance.treasury.domain.identifier.DrawerSessionId;
import tech.kayys.numou.foundation.application.command.Command;
import java.math.BigDecimal;

public record CloseDrawerCommand(
        DrawerSessionId sessionId,
        BigDecimal actualCountedCash,
        String notes
) implements Command {
}
