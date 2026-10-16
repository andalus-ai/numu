package tech.kayys.syirkah.product.domain.classification;

import tech.kayys.syirkah.product.domain.product.ProductId;

/**
 * Connects a {@link ProductId} to a {@link ClassificationNodeId}
 * within a specific {@link ClassificationSchemeId}.
 *
 * This is an association value structure, not an aggregate: it owns no
 * lifecycle of its own and is coordinated by the application layer,
 * which is also where the cross-aggregate rule
 * {@code classification.schemeId == node.schemeId} is enforced
 * (product02.md, sections 7-8). A product may hold many of these, one
 * per taxonomy (retail, accounting, tax, ecommerce, ...).
 */
public record ProductClassification(
        ProductId productId,
        ClassificationSchemeId schemeId,
        ClassificationNodeId nodeId
) {

    public ProductClassification {
        if (productId == null) {
            throw new IllegalArgumentException("productId cannot be null");
        }

        if (schemeId == null) {
            throw new IllegalArgumentException("schemeId cannot be null");
        }

        if (nodeId == null) {
            throw new IllegalArgumentException("nodeId cannot be null");
        }
    }
}
