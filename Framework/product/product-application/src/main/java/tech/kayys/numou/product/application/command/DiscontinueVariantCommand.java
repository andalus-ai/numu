package tech.kayys.syirkah.product.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.product.domain.variant.ProductVariantId;

import java.util.Objects;

/** Discontinues a product variant. */
public record DiscontinueVariantCommand(
        ProductVariantId variantId
) implements Command {

    public DiscontinueVariantCommand {
        Objects.requireNonNull(variantId, "variantId cannot be null");
    }
}
