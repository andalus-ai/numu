package tech.kayys.syirkah.product.spi.port;

import tech.kayys.syirkah.foundation.domain.repository.Repository;
import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.product.domain.sku.Sku;
import tech.kayys.syirkah.product.domain.sku.SkuId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Persistence port for stock keeping units.
 *
 * SKU codes are unique, enforced by the application layer through
 * {@link #existsByCode(String)} before creation.
 */
public interface SkuRepository extends Repository<Sku, SkuId> {

    CompletionStage<Optional<Sku>> findByCode(String code);

    CompletionStage<Boolean> existsByCode(String code);

    CompletionStage<List<Sku>> findByProductId(ProductId productId);
}