package tech.kayys.numou.groceries.application.api.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.groceries.domain.identifier.ProductId;

import java.time.Instant;

public record ProcessWasteCommand(
        ProductId productId,
        String batchNumber,
        int quantity,
        String reason,
        Instant wasteDate
) implements Command {
    public ProcessWasteCommand {
        if (productId == null) throw new IllegalArgumentException("Product ID cannot be null");
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
    }
}
