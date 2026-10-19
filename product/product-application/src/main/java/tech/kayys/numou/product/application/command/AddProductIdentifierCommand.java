package tech.kayys.numou.product.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.product.domain.identifier.ProductIdentifier;
import tech.kayys.numou.product.domain.product.ProductId;

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