package tech.kayys.numou.product.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.product.domain.classification.ClassificationNodeId;
import tech.kayys.numou.product.domain.classification.ClassificationSchemeId;
import tech.kayys.numou.product.domain.product.ProductId;

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
