package tech.kayys.numou.commerce.promotion.domain.target;

import tech.kayys.numou.product.domain.product.ProductId;

import java.util.Objects;

/** Targets a product identity. */
public record ProductTarget(ProductId productId) implements PromotionTarget {
    public ProductTarget {
        Objects.requireNonNull(productId, "productId cannot be null");
    }
}
