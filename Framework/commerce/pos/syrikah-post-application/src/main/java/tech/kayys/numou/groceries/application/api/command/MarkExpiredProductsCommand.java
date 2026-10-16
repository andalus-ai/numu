package tech.kayys.numou.groceries.application.api.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.groceries.domain.identifier.ProductId;

import java.util.List;

public record MarkExpiredProductsCommand(List<ProductId> productIds) implements Command {
    public MarkExpiredProductsCommand {
        if (productIds == null || productIds.isEmpty()) {
            throw new IllegalArgumentException("Product IDs cannot be empty");
        }
    }
}
