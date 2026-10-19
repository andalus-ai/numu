package tech.kayys.numou.product.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.product.domain.product.ProductId;

import java.util.Objects;

/** Archives a discontinued product (terminal state). */
public record ArchiveProductCommand(
        ProductId productId
) implements Command {

    public ArchiveProductCommand {
        Objects.requireNonNull(productId, "productId cannot be null");
    }
}