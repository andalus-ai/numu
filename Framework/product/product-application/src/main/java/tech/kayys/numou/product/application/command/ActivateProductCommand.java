package tech.kayys.syirkah.product.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.product.domain.product.ProductId;

import java.util.Objects;

/** Activates a draft product. */
public record ActivateProductCommand(
        ProductId productId
) implements Command {

    public ActivateProductCommand {
        Objects.requireNonNull(productId, "productId cannot be null");
    }
}