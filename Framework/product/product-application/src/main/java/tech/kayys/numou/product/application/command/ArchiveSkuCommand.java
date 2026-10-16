package tech.kayys.syirkah.product.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.product.domain.sku.SkuId;

import java.util.Objects;

/** Archives a SKU. */
public record ArchiveSkuCommand(
        SkuId skuId
) implements Command {

    public ArchiveSkuCommand {
        Objects.requireNonNull(skuId, "skuId cannot be null");
    }
}
