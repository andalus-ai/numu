package tech.kayys.numou.product.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.product.domain.sku.SkuId;

import java.util.Objects;

/** Activates a SKU. */
public record ActivateSkuCommand(
        SkuId skuId
) implements Command {

    public ActivateSkuCommand {
        Objects.requireNonNull(skuId, "skuId cannot be null");
    }
}
