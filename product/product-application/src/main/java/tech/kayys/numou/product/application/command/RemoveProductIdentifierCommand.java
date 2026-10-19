package tech.kayys.numou.product.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.product.domain.identifier.ProductIdentifier;
import tech.kayys.numou.product.domain.product.ProductId;

import java.util.Objects;

/** Removes an external identifier from a product. */
public record RemoveProductIdentifierCommand(
        ProductId productId,
        ProductIdentifier identifier
) implements Command {

    public RemoveProductIdentifierCommand {
        Objects.requireNonNull(productId, "productId cannot be null");
        Objects.requireNonNull(identifier, "identifier cannot be null");
    }
}
