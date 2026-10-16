package tech.kayys.syirkah.commerce.configuration.spi.port;

import tech.kayys.syirkah.commerce.configuration.domain.ProductConfiguration;
import tech.kayys.syirkah.commerce.configuration.domain.ProductConfigurationId;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Persistence port for product configurations (product02.md). */
public interface ProductConfigurationRepository {

    CompletionStage<ProductConfiguration> save(ProductConfiguration configuration);

    CompletionStage<Optional<ProductConfiguration>> findById(ProductConfigurationId id);

    default CompletionStage<ProductConfiguration> requireById(ProductConfigurationId id) {
        return findById(id).thenApply(maybe -> maybe.orElseThrow(() ->
                new IllegalArgumentException("Configuration not found: " + id.value())));
    }
}
