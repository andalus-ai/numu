package tech.kayys.syirkah.groceries.application.api.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.groceries.domain.identifier.ProductId;

import java.util.List;

public record MarkExpiredProductsCommand(List<ProductId> productIds) implements Command {
    public MarkExpiredProductsCommand {
        if (productIds == null || productIds.isEmpty()) {
            throw new IllegalArgumentException("Product IDs cannot be empty");
        }
    }
}
