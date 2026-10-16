package tech.kayys.syirkah.product.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.product.domain.identifier.ProductIdentifier;
import tech.kayys.syirkah.product.domain.product.ProductId;

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
