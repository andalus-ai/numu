package tech.kayys.numou.product.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.product.domain.classification.ClassificationNodeId;
import tech.kayys.numou.product.domain.classification.ClassificationSchemeId;
import tech.kayys.numou.product.domain.product.ProductId;

import java.util.Objects;

/** Classifies a product under a node of a scheme. */
public record ClassifyProductCommand(
        ProductId productId,
        ClassificationSchemeId schemeId,
        ClassificationNodeId nodeId
) implements Command {

    public ClassifyProductCommand {
        Objects.requireNonNull(productId, "productId cannot be null");
        Objects.requireNonNull(schemeId, "schemeId cannot be null");
        Objects.requireNonNull(nodeId, "nodeId cannot be null");
    }
}
