package tech.kayys.numou.billing.application.api.command;

import tech.kayys.numou.billing.domain.identifier.BillingScheduleId;
import tech.kayys.numou.foundation.application.command.Command;

public record CancelBillingScheduleCommand(
        BillingScheduleId scheduleId,
        String reason
) implements Command {}
