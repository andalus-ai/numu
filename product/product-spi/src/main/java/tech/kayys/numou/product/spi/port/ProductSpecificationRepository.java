package tech.kayys.numou.product.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.product.domain.product.ProductId;
import tech.kayys.numou.product.domain.specification.ProductSpecification;
import tech.kayys.numou.product.domain.specification.ProductSpecificationId;

import java.util.List;
import java.util.concurrent.CompletionStage;

/** Persistence port for product specifications. */
public interface ProductSpecificationRepository
        extends Repository<
        ProductSpecification, ProductSpecificationId> {

    CompletionStage<List<ProductSpecification>> findByProductId(
            ProductId productId
    );

    /** True when the product already owns a specification (product02.md). */
    default CompletionStage<Boolean> existsByProductId(ProductId productId) {
        return findByProductId(productId).thenApply(list -> !list.isEmpty());
    }
}