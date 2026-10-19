package tech.kayys.numou.commerce.offering.application.command;

import tech.kayys.numou.commerce.offering.domain.ProductOfferingId;
import tech.kayys.numou.foundation.application.command.Command;

import java.util.Objects;

public record SuspendProductOfferingCommand(
        ProductOfferingId offeringId
) implements Command {

    public SuspendProductOfferingCommand {
        Objects.requireNonNull(offeringId, "offeringId cannot be null");
    }
}
