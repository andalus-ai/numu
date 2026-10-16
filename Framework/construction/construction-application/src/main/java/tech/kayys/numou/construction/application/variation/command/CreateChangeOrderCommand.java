package tech.kayys.syirkah.construction.application.variation.command;

import tech.kayys.syirkah.construction.domain.variation.ChangeOrderType;
import tech.kayys.syirkah.foundation.application.command.Command;
import java.util.UUID;

public record CreateChangeOrderCommand(
        UUID projectId,
        String orderNumber,
        String title,
        ChangeOrderType type,
        String reason
) implements Command {}
