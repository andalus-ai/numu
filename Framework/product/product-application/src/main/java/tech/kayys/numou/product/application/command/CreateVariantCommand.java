package tech.kayys.syirkah.product.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.product.domain.product.ProductId;

import java.util.Objects;

/** Creates a variant of an existing product. */
public record CreateVariantCommand(
        ProductId productId,
        String code,
        String name
) implements Command {

    public CreateVariantCommand {
        Objects.requireNonNull(productId, "productId cannot be null");
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
    }
}