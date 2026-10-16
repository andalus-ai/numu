package tech.kayys.syirkah.commerce.pricing.application.command;

import tech.kayys.syirkah.commerce.offering.domain.ProductOfferingId;
import tech.kayys.syirkah.commerce.pricing.domain.price.PriceListId;
import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

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
