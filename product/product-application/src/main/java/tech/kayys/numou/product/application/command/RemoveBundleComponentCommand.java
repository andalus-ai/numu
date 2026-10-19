package tech.kayys.numou.product.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.product.domain.bundle.BundleId;
import tech.kayys.numou.product.domain.product.ProductId;

import java.util.Objects;

/** Removes a product component from a draft bundle. */
public record RemoveBundleComponentCommand(
        BundleId bundleId,
        ProductId productId
) implements Command {

    public RemoveBundleComponentCommand {
        Objects.requireNonNull(bundleId, "bundleId cannot be null");
        Objects.requireNonNull(productId, "productId cannot be null");
    }
}
