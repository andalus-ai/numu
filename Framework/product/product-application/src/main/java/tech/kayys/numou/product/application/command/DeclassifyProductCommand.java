package tech.kayys.syirkah.product.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.product.domain.classification.ClassificationNodeId;
import tech.kayys.syirkah.product.domain.classification.ClassificationSchemeId;
import tech.kayys.syirkah.product.domain.product.ProductId;

import java.util.Objects;

/** Removes a product's classification under a node of a scheme. */
public record DeclassifyProductCommand(
        ProductId productId,
        ClassificationSchemeId schemeId,
        ClassificationNodeId nodeId
) implements Command {

    public DeclassifyProductCommand {
        Objects.requireNonNull(productId, "productId cannot be null");
        Objects.requireNonNull(schemeId, "schemeId cannot be null");
        Objects.requireNonNull(nodeId, "nodeId cannot be null");
    }
}
