package tech.kayys.numou.billing.application.api.command;

import tech.kayys.numou.billing.domain.identifier.BillingScheduleId;
import tech.kayys.numou.foundation.application.command.Command;

public record ProcessBillingCycleCommand(
        BillingScheduleId scheduleId
) implements Command {}
