package tech.kayys.syirkah.product.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.product.domain.identifier.ProductIdentifier;
import tech.kayys.syirkah.product.domain.product.ProductId;

import java.util.Objects;

/**
 * Adds an external identifier (SKU, barcode, supplier code, ...)
 * to a product.
 */
public record AddProductIdentifierCommand(
        ProductId productId,
        ProductIdentifier identifier
) implements Command {

    public AddProductIdentifierCommand {
        Objects.requireNonNull(productId, "productId cannot be null");
        Objects.requireNonNull(
                identifier,
                "identifier cannot be null"
        );
    }
}