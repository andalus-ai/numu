package tech.kayys.syirkah.product.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.product.domain.bundle.BundleId;
import tech.kayys.syirkah.product.domain.product.ProductId;

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
