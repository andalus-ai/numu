package tech.kayys.syirkah.commerce.offering.application.command;

import tech.kayys.syirkah.commerce.offering.domain.ProductOfferingId;
import tech.kayys.syirkah.foundation.application.command.Command;

import java.util.Objects;

public record ActivateProductOfferingCommand(
        ProductOfferingId offeringId
) implements Command {

    public ActivateProductOfferingCommand {
        Objects.requireNonNull(offeringId, "offeringId cannot be null");
    }
}
