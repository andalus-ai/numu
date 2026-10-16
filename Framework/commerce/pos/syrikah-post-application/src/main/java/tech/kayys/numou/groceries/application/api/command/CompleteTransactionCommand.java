package tech.kayys.syirkah.groceries.application.api.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import java.util.UUID;

public record CompleteTransactionCommand(
        UUID cartId,
        UUID customerId,
        String paymentMethod,
        String cashierId
) implements Command {
    public CompleteTransactionCommand {
        if (cartId == null) throw new IllegalArgumentException("Cart ID cannot be null");
    }
}
