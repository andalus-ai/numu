package tech.kayys.syirkah.product.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.product.domain.sku.SkuId;
import tech.kayys.syirkah.product.domain.sku.SkuIdentifier;

import java.util.Objects;

/** Removes an external identifier from a SKU. */
public record RemoveSkuIdentifierCommand(
        SkuId skuId,
        SkuIdentifier identifier
) implements Command {

    public RemoveSkuIdentifierCommand {
        Objects.requireNonNull(skuId, "skuId cannot be null");
        Objects.requireNonNull(identifier, "identifier cannot be null");
    }
}
