package tech.kayys.numou.product.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.product.domain.product.ProductId;

import java.util.Objects;

/**
 * Creates a (reusable) specification for an existing product.
 */
public record CreateSpecificationCommand(
        ProductId productId,
        String code,
        String name
) implements Command {

    public CreateSpecificationCommand {
        Objects.requireNonNull(productId, "productId cannot be null");
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
    }
}