package tech.kayys.numou.billing.application.api.command;

import tech.kayys.numou.foundation.application.command.Command;
import java.time.Instant;

public record BatchBillingCommand(
        String batchId,
        Instant processDate
) implements Command {}
