package tech.kayys.syirkah.product.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.product.domain.variant.ProductVariantId;

import java.util.Objects;

/** Activates a product variant. */
public record ActivateVariantCommand(
        ProductVariantId variantId
) implements Command {

    public ActivateVariantCommand {
        Objects.requireNonNull(variantId, "variantId cannot be null");
    }
}
