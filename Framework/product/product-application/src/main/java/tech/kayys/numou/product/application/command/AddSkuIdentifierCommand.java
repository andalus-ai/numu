package tech.kayys.syirkah.product.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.product.domain.sku.SkuId;
import tech.kayys.syirkah.product.domain.sku.SkuIdentifier;

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