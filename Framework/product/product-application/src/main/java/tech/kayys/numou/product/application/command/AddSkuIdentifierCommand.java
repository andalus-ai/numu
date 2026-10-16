package tech.kayys.numou.product.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.product.domain.sku.SkuId;
import tech.kayys.numou.product.domain.sku.SkuIdentifier;

import java.util.Objects;

/**
 * Adds an external identifier (barcode, EAN, supplier code, ...) to
 * a stock keeping unit.
 */
public record AddSkuIdentifierCommand(
        SkuId skuId,
        SkuIdentifier identifier
) implements Command {

    public AddSkuIdentifierCommand {
        Objects.requireNonNull(skuId, "skuId cannot be null");
        Objects.requireNonNull(
                identifier,
                "identifier cannot be null"
        );
    }
}