package tech.kayys.syirkah.product.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.product.domain.product.ProductId;

import java.util.Objects;

/**
 * Updates the mutable descriptive data of a product (name and
 * description). Code and type are identity, never edited here.
 */
public record UpdateProductCommand(
        ProductId productId,
        String name,
        String description
) implements Command {

    public UpdateProductCommand {
        Objects.requireNonNull(productId, "productId cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
    }
}