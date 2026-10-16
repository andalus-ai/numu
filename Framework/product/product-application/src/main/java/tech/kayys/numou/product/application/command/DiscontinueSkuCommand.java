package tech.kayys.syirkah.product.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.product.domain.sku.SkuId;

import java.util.Objects;

/** Discontinues a SKU. */
public record DiscontinueSkuCommand(
        SkuId skuId
) implements Command {

    public DiscontinueSkuCommand {
        Objects.requireNonNull(skuId, "skuId cannot be null");
    }
}
