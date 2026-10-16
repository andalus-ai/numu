package tech.kayys.numou.product.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.product.domain.bundle.BundleId;
import tech.kayys.numou.product.domain.product.ProductId;

import java.math.BigDecimal;
import java.util.Objects;

/** Adds a product component to a draft bundle. */
public record AddBundleComponentCommand(
        BundleId bundleId,
        ProductId productId,
        BigDecimal quantity
) implements Command {

    public AddBundleComponentCommand {
        Objects.requireNonNull(bundleId, "bundleId cannot be null");
        Objects.requireNonNull(productId, "productId cannot be null");
        Objects.requireNonNull(quantity, "quantity cannot be null");
    }
}
