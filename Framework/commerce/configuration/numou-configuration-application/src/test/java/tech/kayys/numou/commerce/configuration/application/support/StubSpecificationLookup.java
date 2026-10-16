package tech.kayys.syirkah.commerce.configuration.application.support;

import tech.kayys.syirkah.commerce.configuration.spi.port.SpecificationLookupPort;
import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.product.domain.specification.ProductSpecification;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Test double: preset specifications per product.
 */
public final class StubSpecificationLookup implements SpecificationLookupPort {

    private final List<ProductSpecification> specifications = new ArrayList<>();

    public void add(ProductSpecification specification) {
        specifications.add(specification);
    }

    @Override
    public CompletionStage<List<ProductSpecification>> findSpecificationsByProduct(
            ProductId productId
    ) {
        return CompletableFuture.completedFuture(specifications.stream()
                .filter(spec -> spec.productId().equals(productId))
                .toList());
    }
}
