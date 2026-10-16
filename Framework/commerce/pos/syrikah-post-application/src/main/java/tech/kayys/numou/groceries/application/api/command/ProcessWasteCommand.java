package tech.kayys.syirkah.groceries.application.api.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.groceries.domain.identifier.ProductId;

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
