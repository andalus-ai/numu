package tech.kayys.numou.finance.treasury.application.api.command;

import tech.kayys.numou.foundation.application.command.Command;
import java.math.BigDecimal;

public record OpenDrawerCommand(
        String registerId,
        String cashierId,
        String currencyCode,
        BigDecimal openingFloat
) implements Command {
}
