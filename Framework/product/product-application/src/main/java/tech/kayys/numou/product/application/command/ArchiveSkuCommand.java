package tech.kayys.numou.product.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.product.domain.sku.SkuId;

import java.util.Objects;

/** Archives a SKU. */
public record ArchiveSkuCommand(
        SkuId skuId
) implements Command {

    public ArchiveSkuCommand {
        Objects.requireNonNull(skuId, "skuId cannot be null");
    }
}
