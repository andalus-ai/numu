package tech.kayys.numou.product.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.product.domain.product.ProductId;
import tech.kayys.numou.product.domain.variant.ProductVariant;
import tech.kayys.numou.product.domain.variant.ProductVariantId;

import java.util.List;
import java.util.concurrent.CompletionStage;

/** Persistence port for product variants. */
public interface ProductVariantRepository
        extends Repository<ProductVariant, ProductVariantId> {

    CompletionStage<List<ProductVariant>> findByProductId(
            ProductId productId
    );
}