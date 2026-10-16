package tech.kayys.syirkah.product.spi.port;

import tech.kayys.syirkah.foundation.domain.repository.Repository;
import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.product.domain.variant.ProductVariant;
import tech.kayys.syirkah.product.domain.variant.ProductVariantId;

import java.util.List;
import java.util.concurrent.CompletionStage;

/** Persistence port for product variants. */
public interface ProductVariantRepository
        extends Repository<ProductVariant, ProductVariantId> {

    CompletionStage<List<ProductVariant>> findByProductId(
            ProductId productId
    );
}