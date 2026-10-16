package tech.kayys.syirkah.product.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.product.domain.sku.SkuId;

import java.util.Objects;

/** Activates a SKU. */
public record ActivateSkuCommand(
        SkuId skuId
) implements Command {

    public ActivateSkuCommand {
        Objects.requireNonNull(skuId, "skuId cannot be null");
    }
}
