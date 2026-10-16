package tech.kayys.numou.construction.application.variation.command;

import tech.kayys.numou.construction.domain.variation.ChangeOrderType;
import tech.kayys.numou.foundation.application.command.Command;
import java.util.UUID;

public record CreateChangeOrderCommand(
        UUID projectId,
        String orderNumber,
        String title,
        ChangeOrderType type,
        String reason
) implements Command {}
