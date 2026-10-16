package tech.kayys.syirkah.groceries.application.api.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.groceries.domain.identifier.ProductId;
import tech.kayys.syirkah.groceries.domain.identifier.ScaleId;
import tech.kayys.syirkah.groceries.domain.valueobject.Weight;
import java.util.UUID;

public record AddWeightedItemCommand(
        UUID cartId,
        ProductId productId,
        ScaleId scaleId,
        Weight weight,
        Double unitPricePerKg
) implements Command {
    public AddWeightedItemCommand {
        if (cartId == null) throw new IllegalArgumentException("Cart ID cannot be null");
        if (productId == null) throw new IllegalArgumentException(" product ID cannot be null");
        if (scaleId == null) throw new IllegalArgumentException("Scale ID cannot be null");
    }
}
