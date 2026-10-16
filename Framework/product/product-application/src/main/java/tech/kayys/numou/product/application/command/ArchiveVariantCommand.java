package tech.kayys.syirkah.product.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.product.domain.variant.ProductVariantId;

import java.util.Objects;

/** Archives a product variant. */
public record ArchiveVariantCommand(
        ProductVariantId variantId
) implements Command {

    public ArchiveVariantCommand {
        Objects.requireNonNull(variantId, "variantId cannot be null");
    }
}
