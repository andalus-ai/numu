package tech.kayys.syirkah.product.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.product.domain.product.ProductId;

import java.util.Objects;

/**
 * Discontinues an active product. Products with historical
 * transactions are never deleted - they move to DISCONTINUED.
 */
public record DiscontinueProductCommand(
        ProductId productId
) implements Command {

    public DiscontinueProductCommand {
        Objects.requireNonNull(productId, "productId cannot be null");
    }
}