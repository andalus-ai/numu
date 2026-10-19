package tech.kayys.numou.commerce.pricing.application.command;

import tech.kayys.numou.commerce.pricing.domain.price.PriceListId;
import tech.kayys.numou.foundation.application.command.Command;

import java.util.Objects;

/** Lifecycle transition for a price list. */
public record ActivatePriceListCommand(
        PriceListId priceListId
) implements Command {

    public ActivatePriceListCommand {
        Objects.requireNonNull(priceListId, "priceListId cannot be null");
    }
}
