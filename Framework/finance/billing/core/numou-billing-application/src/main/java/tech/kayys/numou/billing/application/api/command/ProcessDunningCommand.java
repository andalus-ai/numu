package tech.kayys.numou.billing.application.api.command;

import tech.kayys.numou.billing.domain.valueobject.DunningAction;
import tech.kayys.numou.foundation.application.command.Command;

public record ProcessDunningCommand(
        String customerId,
        int daysOverdue,
        DunningAction action
) implements Command {
    public ProcessDunningCommand(String customerId) {
        this(customerId, 0, null);
    }
    public ProcessDunningCommand(int daysOverdue, DunningAction action) {
        this(null, daysOverdue, action);
    }
}
