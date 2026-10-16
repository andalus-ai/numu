package tech.kayys.numou.product.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.product.domain.product.ProductId;
import tech.kayys.numou.product.domain.variant.ProductVariantId;

/** Creates a stock keeping unit for a product (and optional variant). */
public record CreateSkuCommand(
        ProductId productId,
        ProductVariantId variantId,
        String code,
        String name
) implements Command {

    public CreateSkuCommand {
        if (productId == null) {
            throw new IllegalArgumentException(
                    "productId cannot be null"
            );
        }

        if (code == null) {
            throw new IllegalArgumentException("code cannot be null");
        }

        if (name == null) {
            throw new IllegalArgumentException("name cannot be null");
        }
    }
}