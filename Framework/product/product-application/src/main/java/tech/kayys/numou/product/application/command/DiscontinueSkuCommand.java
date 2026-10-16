package tech.kayys.numou.product.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.product.domain.sku.SkuId;

import java.util.Objects;

/** Discontinues a SKU. */
public record DiscontinueSkuCommand(
        SkuId skuId
) implements Command {

    public DiscontinueSkuCommand {
        Objects.requireNonNull(skuId, "skuId cannot be null");
    }
}
