package tech.kayys.syirkah.commerce.offering.application.command;

import tech.kayys.syirkah.commerce.offering.domain.ProductOfferingId;
import tech.kayys.syirkah.foundation.application.command.Command;

import java.util.Objects;

public record RetireProductOfferingCommand(
        ProductOfferingId offeringId
) implements Command {

    public RetireProductOfferingCommand {
        Objects.requireNonNull(offeringId, "offeringId cannot be null");
    }
}
