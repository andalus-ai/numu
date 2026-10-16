package tech.kayys.syirkah.commerce.pricing.application.command;

import tech.kayys.syirkah.commerce.pricing.domain.price.PriceListId;
import tech.kayys.syirkah.foundation.application.command.Command;

import java.util.Objects;

/** Lifecycle transition for a price list. */
public record ActivatePriceListCommand(
        PriceListId priceListId
) implements Command {

    public ActivatePriceListCommand {
        Objects.requireNonNull(priceListId, "priceListId cannot be null");
    }
}
