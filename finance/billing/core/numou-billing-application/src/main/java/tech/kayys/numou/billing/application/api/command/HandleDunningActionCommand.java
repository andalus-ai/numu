package tech.kayys.numou.billing.application.api.command;

import tech.kayys.numou.billing.domain.identifier.BillingScheduleId;
import tech.kayys.numou.billing.domain.valueobject.DunningAction;
import tech.kayys.numou.foundation.application.command.Command;

public record HandleDunningActionCommand(
        BillingScheduleId scheduleId,
        DunningAction action
) implements Command {}
