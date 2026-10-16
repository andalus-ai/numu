package tech.kayys.syirkah.product.spi.port;

import tech.kayys.syirkah.foundation.domain.repository.Repository;
import tech.kayys.syirkah.product.domain.product.Product;
import tech.kayys.syirkah.product.domain.product.ProductId;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Persistence port for products.
 *
 * Product codes are unique per tenant, which the application layer
 * enforces through {@link #existsByCode(String)} before creation.
 */
public interface ProductRepository
        extends Repository<Product, ProductId> {

    CompletionStage<Optional<Product>> findByCode(String code);

    CompletionStage<Boolean> existsByCode(String code);
}