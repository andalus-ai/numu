package tech.kayys.numou.product.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.product.domain.variant.ProductVariantId;

import java.util.Objects;

/** Activates a product variant. */
public record ActivateVariantCommand(
        ProductVariantId variantId
) implements Command {

    public ActivateVariantCommand {
        Objects.requireNonNull(variantId, "variantId cannot be null");
    }
}
