package tech.kayys.syirkah.commerce.configuration.spi.port;

import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.product.domain.specification.ProductSpecification;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Read port for configuration validation.
 *
 * The configuration capability owns no product data — it resolves the
 * product's specifications through this port (backed by
 * {@code product-spi} in production, by a stub in tests). Multiple
 * specifications per product are supported (spec evolution v1/v2/v3):
 * the validator checks the selection against every specification and
 * accepts it when at least one has no violations.
 */
public interface SpecificationLookupPort {

    CompletionStage<List<ProductSpecification>> findSpecificationsByProduct(
            ProductId productId);
}
