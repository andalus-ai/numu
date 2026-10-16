package tech.kayys.syirkah.product.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.product.domain.product.ProductId;

import java.util.Objects;

/** Archives a discontinued product (terminal state). */
public record ArchiveProductCommand(
        ProductId productId
) implements Command {

    public ArchiveProductCommand {
        Objects.requireNonNull(productId, "productId cannot be null");
    }
}