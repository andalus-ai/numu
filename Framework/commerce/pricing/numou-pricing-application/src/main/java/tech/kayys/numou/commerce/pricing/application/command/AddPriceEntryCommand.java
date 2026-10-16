package tech.kayys.numou.commerce.pricing.application.command;

import tech.kayys.numou.commerce.offering.domain.ProductOfferingId;
import tech.kayys.numou.commerce.pricing.domain.price.PriceListId;
import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.util.Objects;

/** Adds or changes one price-list row. */
public record AddPriceEntryCommand(
        PriceListId priceListId,
        ProductOfferingId offeringId,
        Money amount
) implements Command {

    public AddPriceEntryCommand {
        Objects.requireNonNull(priceListId, "priceListId cannot be null");
        Objects.requireNonNull(offeringId, "offeringId cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
    }
}
