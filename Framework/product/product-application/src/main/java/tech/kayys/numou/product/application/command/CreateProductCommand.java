package tech.kayys.numou.product.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.product.domain.product.ProductType;

import java.util.Objects;

/** Creates a draft product. */
public record CreateProductCommand(
        String code,
        String name,
        String description,
        ProductType type
) implements Command {

    public CreateProductCommand {
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
    }
}